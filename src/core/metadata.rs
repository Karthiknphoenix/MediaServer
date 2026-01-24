use serde::Deserialize;
use std::env;
use std::error::Error;

#[derive(Deserialize, Debug)]
pub struct OmdbResponse {
    #[serde(rename = "Title")]
    pub title: String,
    #[serde(rename = "Year")]
    pub year: String,
    #[serde(rename = "Poster")]
    pub poster: String,
    #[serde(rename = "Plot")]
    pub plot: String,
    #[serde(rename = "Type")]
    pub media_type: String,
}

pub async fn fetch_metadata(title: &str, pool: &sqlx::SqlitePool) -> Result<OmdbResponse, Box<dyn Error>> {
    // Try to get API key from DB
    let api_key: Option<String> = sqlx::query_scalar("SELECT value FROM settings WHERE key = 'omdb_api_key'")
        .fetch_optional(pool)
        .await
        .unwrap_or(None);

    // Fallback to Env Ref (optional, or just error if missing)
    let api_key = api_key.or(env::var("OMDB_API_KEY").ok()).ok_or("No OMDB API Key found")?;

    let url = format!("http://www.omdbapi.com/?t={}&apikey={}", title, api_key);

    let resp = reqwest::get(&url)
        .await?
        .json::<OmdbResponse>()
        .await?;

    Ok(resp)
}
