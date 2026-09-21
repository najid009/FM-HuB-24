//! FM-HuB-24 provider core.
//!
//! The Android UI talks to this boundary rather than to extension bytecode. The provider source
//! is compiled into the application; this crate never downloads executable code.

mod client;
mod error;
mod jni_bridge;
mod models;

pub use client::{ProviderClient, ProviderConfig, ProviderCore};
pub use error::ProviderError;
pub use models::{
    CatalogItem, CatalogSection, Episode, MediaDetails, MediaKind, Page, Season, StreamSource,
    Subtitle,
};

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn invalid_provider_url_is_rejected_before_network_access() {
        let error = ProviderConfig::new("not a url", "FM-HuB/24").unwrap_err();
        assert!(matches!(error, ProviderError::Configuration(_)));
    }

    #[test]
    fn page_defaults_are_stable() {
        let page: Page<CatalogItem> = Page::new(vec![], 1, false);
        assert_eq!(page.page, 1);
        assert!(!page.has_next);
    }
}
