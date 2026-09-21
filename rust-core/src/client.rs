use crate::{
    CatalogItem, CatalogSection, Episode, MediaDetails, Page, ProviderError, StreamSource, Subtitle,
};
use reqwest::{Client, Method, StatusCode};
use serde::de::DeserializeOwned;
use std::{sync::Arc, time::Duration};
use tokio::sync::RwLock;
use url::Url;

const DEFAULT_TIMEOUT: Duration = Duration::from_secs(15);
const DEFAULT_RETRIES: usize = 2;

#[derive(Debug, Clone)]
pub struct ProviderConfig {
    pub base_url: Url,
    pub user_agent: String,
    pub timeout: Duration,
    pub retries: usize,
}

impl ProviderConfig {
    pub fn new(base_url: &str, user_agent: &str) -> Result<Self, ProviderError> {
        let parsed =
            Url::parse(base_url).map_err(|e| ProviderError::Configuration(e.to_string()))?;
        match parsed.scheme() {
            "https" => Ok(Self {
                base_url: parsed,
                user_agent: user_agent.trim().to_string(),
                timeout: DEFAULT_TIMEOUT,
                retries: DEFAULT_RETRIES,
            }),
            scheme => Err(ProviderError::Configuration(format!(
                "HTTPS is required, got {scheme}"
            ))),
        }
    }
}

pub trait ProviderClient: Send + Sync {
    fn config(&self) -> &ProviderConfig;
}

#[derive(Clone)]
pub struct ProviderCore {
    client: Client,
    config: ProviderConfig,
    home_cache: Arc<RwLock<Option<(u32, Vec<CatalogSection>)>>>,
}

impl ProviderClient for ProviderCore {
    fn config(&self) -> &ProviderConfig {
        &self.config
    }
}

impl ProviderCore {
    pub fn new(config: ProviderConfig) -> Result<Self, ProviderError> {
        let client = Client::builder()
            .timeout(config.timeout)
            .user_agent(config.user_agent.clone())
            .build()
            .map_err(|e| ProviderError::Configuration(e.to_string()))?;
        Ok(Self {
            client,
            config,
            home_cache: Arc::new(RwLock::new(None)),
        })
    }

    pub async fn home(
        &self,
        page: u32,
        force_refresh: bool,
    ) -> Result<Vec<CatalogSection>, ProviderError> {
        if !force_refresh {
            if let Some((cached_page, cached)) = self.home_cache.read().await.clone() {
                if cached_page == page {
                    return Ok(cached);
                }
            }
        }
        let sections: Vec<CatalogSection> =
            self.get("/home", &[(&"page", page.to_string())]).await?;
        *self.home_cache.write().await = Some((page, sections.clone()));
        Ok(sections)
    }

    pub async fn search(&self, query: &str, page: u32) -> Result<Page<CatalogItem>, ProviderError> {
        self.get(
            "/search",
            &[("q", query.to_string()), ("page", page.to_string())],
        )
        .await
    }

    pub async fn details(&self, id: &str) -> Result<MediaDetails, ProviderError> {
        self.get(&format!("/details/{id}"), &[]).await
    }

    pub async fn episodes(
        &self,
        id: &str,
        season: u16,
        page: u32,
    ) -> Result<Page<Episode>, ProviderError> {
        self.get(
            &format!("/details/{id}/season/{season}"),
            &[("page", page.to_string())],
        )
        .await
    }

    pub async fn streams(&self, episode_id: &str) -> Result<Vec<StreamSource>, ProviderError> {
        self.get(&format!("/streams/{episode_id}"), &[]).await
    }

    pub async fn subtitles(&self, episode_id: &str) -> Result<Vec<Subtitle>, ProviderError> {
        self.get(&format!("/subtitles/{episode_id}"), &[]).await
    }

    async fn get<T: DeserializeOwned>(
        &self,
        path: &str,
        query: &[(&str, String)],
    ) -> Result<T, ProviderError> {
        let mut url = self
            .config
            .base_url
            .join(path.trim_start_matches('/'))
            .map_err(|e| ProviderError::Configuration(e.to_string()))?;
        url.query_pairs_mut()
            .extend_pairs(query.iter().map(|(k, v)| (*k, v.as_str())));
        for attempt in 0..=self.config.retries {
            let response = self.client.request(Method::GET, url.clone()).send().await;
            match response {
                Ok(response) if response.status().is_success() => {
                    return response
                        .json()
                        .await
                        .map_err(|e| ProviderError::InvalidData(e.to_string()));
                }
                Ok(response)
                    if response.status() == StatusCode::TOO_MANY_REQUESTS
                        || response.status().is_server_error() =>
                {
                    if attempt < self.config.retries {
                        tokio::time::sleep(Duration::from_millis(200 * (attempt as u64 + 1))).await;
                        continue;
                    }
                    return Err(ProviderError::Unavailable);
                }
                Ok(response) => return Err(ProviderError::HttpStatus(response.status().as_u16())),
                Err(error) if error.is_timeout() => {
                    if attempt < self.config.retries {
                        continue;
                    }
                    return Err(ProviderError::Timeout);
                }
                Err(error) => return Err(ProviderError::Transport(error.to_string())),
            }
        }
        Err(ProviderError::Unavailable)
    }
}
