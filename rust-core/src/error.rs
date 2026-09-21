use thiserror::Error;

#[derive(Debug, Error)]
pub enum ProviderError {
    #[error("provider configuration is invalid: {0}")]
    Configuration(String),
    #[error("provider request timed out")]
    Timeout,
    #[error("provider is unavailable after retries")]
    Unavailable,
    #[error("provider returned HTTP status {0}")]
    HttpStatus(u16),
    #[error("provider returned invalid data: {0}")]
    InvalidData(String),
    #[error("provider transport failed: {0}")]
    Transport(String),
}
