use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum MediaKind {
    Movie,
    Series,
    Episode,
    Unknown,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct CatalogItem {
    pub id: String,
    pub title: String,
    pub poster_url: Option<String>,
    pub backdrop_url: Option<String>,
    pub year: Option<u16>,
    pub kind: MediaKind,
    pub rating: Option<f32>,
    pub description: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct CatalogSection {
    pub id: String,
    pub title: String,
    pub items: Vec<CatalogItem>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Page<T> {
    pub items: Vec<T>,
    pub page: u32,
    pub has_next: bool,
}

impl<T> Page<T> {
    pub fn new(items: Vec<T>, page: u32, has_next: bool) -> Self {
        Self {
            items,
            page: page.max(1),
            has_next,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct MediaDetails {
    pub item: CatalogItem,
    pub genres: Vec<String>,
    pub audio_languages: Vec<String>,
    pub seasons: Vec<Season>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Season {
    pub number: u16,
    pub title: Option<String>,
    pub episodes: Vec<Episode>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Episode {
    pub id: String,
    pub number: u16,
    pub title: Option<String>,
    pub description: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct StreamSource {
    pub url: String,
    pub quality: Option<u32>,
    pub mime_type: Option<String>,
    pub referer: Option<String>,
    pub headers: Vec<(String, String)>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Subtitle {
    pub url: String,
    pub language: String,
    pub format: Option<String>,
}
