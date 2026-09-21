use crate::{ProviderConfig, ProviderCore, ProviderError};
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jstring};
use jni::JNIEnv;
use once_cell::sync::Lazy;
use serde::Serialize;
use serde_json::{json, Value};
use std::sync::Mutex;
use tokio::runtime::{Builder, Runtime};

static RUNTIME: Lazy<Mutex<Runtime>> = Lazy::new(|| {
    Mutex::new(
        Builder::new_current_thread()
            .enable_all()
            .build()
            .expect("provider runtime must initialize"),
    )
});
static PROVIDER: Lazy<Mutex<Option<ProviderCore>>> = Lazy::new(|| Mutex::new(None));

#[derive(Serialize)]
struct BridgeError<'a> {
    kind: &'a str,
    message: String,
}

fn error_kind(error: &ProviderError) -> &'static str {
    match error {
        ProviderError::Configuration(_) => "configuration",
        ProviderError::Timeout => "timeout",
        ProviderError::Unavailable => "unavailable",
        ProviderError::HttpStatus(_) => "http_status",
        ProviderError::InvalidData(_) => "invalid_data",
        ProviderError::Transport(_) => "transport",
    }
}

fn error_response(error: ProviderError) -> String {
    serde_json::to_string(&json!({
        "ok": false,
        "error": BridgeError { kind: error_kind(&error), message: error.to_string() }
    }))
    .unwrap_or_else(|_| {
        r#"{"ok":false,"error":{"kind":"internal","message":"provider error"}}"#.to_string()
    })
}

fn success_response<T: Serialize>(value: T) -> String {
    serde_json::to_string(&json!({ "ok": true, "data": value }))
        .unwrap_or_else(|_| r#"{"ok":false,"error":{"kind":"serialization","message":"provider response could not be encoded"}}"#.to_string())
}

fn into_jstring(env: &mut JNIEnv<'_>, payload: String) -> jstring {
    env.new_string(payload)
        .map(|value| value.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

fn read_string(env: &mut JNIEnv<'_>, value: JString<'_>) -> Result<String, ProviderError> {
    env.get_string(&value)
        .map(|value| value.to_string_lossy().into_owned())
        .map_err(|error| ProviderError::Configuration(format!("invalid JNI string: {error}")))
}

fn configured() -> Result<ProviderCore, ProviderError> {
    PROVIDER
        .lock()
        .map_err(|_| ProviderError::Unavailable)?
        .clone()
        .ok_or_else(|| ProviderError::Configuration("provider has not been configured".to_string()))
}

fn run_with_provider<F, Fut, T>(operation: F) -> String
where
    F: FnOnce(ProviderCore) -> Fut,
    Fut: std::future::Future<Output = Result<T, ProviderError>>,
    T: Serialize,
{
    match configured() {
        Ok(provider) => match RUNTIME.lock() {
            Ok(runtime) => match runtime.block_on(operation(provider)) {
                Ok(value) => success_response(value),
                Err(error) => error_response(error),
            },
            Err(_) => error_response(ProviderError::Unavailable),
        },
        Err(error) => error_response(error),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fmhub24_app_data_provider_RustProviderBridge_configure(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    base_url: JString<'_>,
    user_agent: JString<'_>,
) -> jstring {
    let result = (|| {
        let base_url = read_string(&mut env, base_url)?;
        let user_agent = read_string(&mut env, user_agent)?;
        let config = ProviderConfig::new(&base_url, &user_agent)?;
        let provider = ProviderCore::new(config)?;
        *PROVIDER.lock().map_err(|_| ProviderError::Unavailable)? = Some(provider);
        Ok::<Value, ProviderError>(json!({ "configured": true }))
    })();
    into_jstring(
        &mut env,
        result.map(success_response).unwrap_or_else(error_response),
    )
}

#[no_mangle]
pub extern "system" fn Java_com_fmhub24_app_data_provider_RustProviderBridge_home(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    page: jint,
    force_refresh: jboolean,
) -> jstring {
    into_jstring(&mut env, home_payload(page, force_refresh))
}

fn home_payload(page: jint, force_refresh: jboolean) -> String {
    run_with_provider(|provider| async move {
        provider.home(page.max(1) as u32, force_refresh != 0).await
    })
}

fn search_payload(query: String, page: jint) -> String {
    run_with_provider(|provider| async move { provider.search(&query, page.max(1) as u32).await })
}

fn details_payload(id: String) -> String {
    run_with_provider(|provider| async move { provider.details(&id).await })
}

fn episodes_payload(id: String, season: jint, page: jint) -> String {
    run_with_provider(|provider| async move {
        provider
            .episodes(&id, season.max(0) as u16, page.max(1) as u32)
            .await
    })
}

fn streams_payload(id: String) -> String {
    run_with_provider(|provider| async move { provider.streams(&id).await })
}

fn subtitles_payload(id: String) -> String {
    run_with_provider(|provider| async move { provider.subtitles(&id).await })
}

#[no_mangle]
pub extern "system" fn Java_com_fmhub24_app_data_provider_RustProviderBridge_search(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    query: JString<'_>,
    page: jint,
) -> jstring {
    match read_string(&mut env, query) {
        Ok(query) => into_jstring(&mut env, search_payload(query, page)),
        Err(error) => into_jstring(&mut env, error_response(error)),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fmhub24_app_data_provider_RustProviderBridge_details(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    id: JString<'_>,
) -> jstring {
    match read_string(&mut env, id) {
        Ok(id) => into_jstring(&mut env, details_payload(id)),
        Err(error) => into_jstring(&mut env, error_response(error)),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fmhub24_app_data_provider_RustProviderBridge_episodes(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    id: JString<'_>,
    season: jint,
    page: jint,
) -> jstring {
    match read_string(&mut env, id) {
        Ok(id) => into_jstring(&mut env, episodes_payload(id, season, page)),
        Err(error) => into_jstring(&mut env, error_response(error)),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fmhub24_app_data_provider_RustProviderBridge_streams(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    id: JString<'_>,
) -> jstring {
    match read_string(&mut env, id) {
        Ok(id) => into_jstring(&mut env, streams_payload(id)),
        Err(error) => into_jstring(&mut env, error_response(error)),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fmhub24_app_data_provider_RustProviderBridge_subtitles(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    id: JString<'_>,
) -> jstring {
    match read_string(&mut env, id) {
        Ok(id) => into_jstring(&mut env, subtitles_payload(id)),
        Err(error) => into_jstring(&mut env, error_response(error)),
    }
}
