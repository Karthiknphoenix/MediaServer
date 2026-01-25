use serde::{Deserialize, Serialize};
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
    #[serde(default)] 
    pub backdrop: Option<String>,
    #[serde(default)]
    pub tmdb_id: Option<i64>,
    #[serde(default)]
    pub runtime: Option<i32>,
    #[serde(default)]
    pub genres: Option<Vec<String>>,
}

#[derive(Deserialize, Debug)]
struct TmdbFullResponse {
    pub runtime: Option<i32>,
    pub episode_run_time: Option<Vec<i32>>,
    pub genres: Option<Vec<TmdbGenre>>,
}

#[derive(Deserialize, Debug)]
struct TmdbGenre {
    pub name: String,
}

#[derive(Deserialize, Debug)]
struct TmdbResponse {
    results: Vec<TmdbResult>,
}

#[derive(Deserialize, Debug)]
struct TmdbResult {
    id: i64,
    #[serde(alias = "title", alias = "name")]
    title: String,
    #[serde(alias = "overview")]
    overview: Option<String>,
    #[serde(alias = "poster_path")]
    poster_path: Option<String>,
    #[serde(alias = "backdrop_path")]
    backdrop_path: Option<String>,
    #[serde(alias = "release_date", alias = "first_air_date")]
    date: Option<String>,
}

// Public DTO for API responses
#[derive(Serialize, Debug)]
pub struct TmdbSearchResult {
    pub id: i64,
    pub title: String,
    pub year: String,
    pub poster_url: String,
    pub overview: String,
}

#[derive(Deserialize, Debug)]
pub struct TmdbSeasonResponse {
    pub episodes: Vec<TmdbEpisode>,
}

#[derive(Deserialize, Debug)]
pub struct TmdbEpisode {
    pub episode_number: i32,
    pub name: String,
    pub overview: String,
    pub still_path: Option<String>,
}

fn build_client() -> Result<reqwest::Client, Box<dyn Error + Send + Sync>> {
    Ok(reqwest::Client::builder()
        .user_agent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        .build()?)
}

pub async fn search_tmdb(query: &str, media_type: Option<&str>, api_key: &str) -> Result<Vec<TmdbSearchResult>, Box<dyn Error + Send + Sync>> {
    let endpoint = match media_type {
        Some("tv") | Some("series") => "search/tv",
        _ => "search/movie",
    };
    let url = format!("https://api.themoviedb.org/3/{}", endpoint);
    
    let client = build_client()?;
    let resp = client.get(&url)
        .query(&[("api_key", api_key), ("query", query)])
        .send().await?
        .json::<TmdbResponse>().await?;

    Ok(resp.results.into_iter().map(|r| TmdbSearchResult {
        id: r.id,
        title: r.title,
        year: r.date.map(|d| d.chars().take(4).collect()).unwrap_or_default(),
        poster_url: r.poster_path.map(|p| format!("https://image.tmdb.org/t/p/w500{}", p)).unwrap_or_default(),
        overview: r.overview.unwrap_or_default(),
    }).collect())
}

pub async fn fetch_tmdb_by_id(tmdb_id: i64, media_type: Option<&str>, api_key: &str) -> Result<OmdbResponse, Box<dyn Error + Send + Sync>> {
    let endpoint = match media_type {
        Some("tv") | Some("series") => format!("tv/{}", tmdb_id),
        _ => format!("movie/{}", tmdb_id),
    };
    let url = format!("https://api.themoviedb.org/3/{}", endpoint);
    
    let client = build_client()?;
    let resp: serde_json::Value = client.get(&url)
        .query(&[("api_key", api_key)])
        .send().await?
        .json().await?;

    let full_info: TmdbFullResponse = serde_json::from_value(resp.clone()).unwrap_or(TmdbFullResponse {
        runtime: None,
        episode_run_time: None,
        genres: None,
    });

    let title = resp.get("title").or(resp.get("name"))
        .and_then(|v| v.as_str()).unwrap_or("Unknown").to_string();
    let year = resp.get("release_date").or(resp.get("first_air_date"))
        .and_then(|v| v.as_str()).map(|d| d.chars().take(4).collect()).unwrap_or_default();
    let poster = resp.get("poster_path")
        .and_then(|v| v.as_str()).map(|p| format!("https://image.tmdb.org/t/p/w500{}", p)).unwrap_or("N/A".to_string());
    let backdrop = resp.get("backdrop_path")
        .and_then(|v| v.as_str()).map(|p| format!("https://image.tmdb.org/t/p/original{}", p));
    let plot = resp.get("overview")
        .and_then(|v| v.as_str()).unwrap_or("").to_string();
    let media_type_str = match media_type {
        Some("tv") | Some("series") => "series",
        _ => "movie",
    }.to_string();

    let runtime = full_info.runtime.or_else(|| {
        full_info.episode_run_time.as_ref().and_then(|v| v.first().copied())
    });

    let genres = full_info.genres.map(|gs| gs.into_iter().map(|g| g.name).collect());

    Ok(OmdbResponse { 
        title, 
        year, 
        poster, 
        plot, 
        media_type: media_type_str, 
        backdrop,
        tmdb_id: Some(tmdb_id),
        runtime,
        genres,
    })
}


pub async fn fetch_metadata(title: &str, media_type_hint: Option<&str>, pool: &sqlx::SqlitePool) -> Result<OmdbResponse, Box<dyn Error + Send + Sync>> {
    // 1. Try TMDB First
    let tmdb_key: Option<String> = sqlx::query_scalar("SELECT value FROM settings WHERE key = 'tmdb_api_key'")
        .fetch_optional(pool)
        .await
        .unwrap_or(None);

    if let Some(key) = tmdb_key {
        if !key.is_empty() {
             return fetch_tmdb_metadata(title, media_type_hint, &key).await;
        }
    }

    Err("TMDB API Key not configured or fetch failed".into())
}

async fn fetch_tmdb_metadata(title: &str, media_type: Option<&str>, api_key: &str) -> Result<OmdbResponse, Box<dyn Error + Send + Sync>> {
    let base_url = "https://api.themoviedb.org/3";
    let endpoint = match media_type {
        Some("series") | Some("tv") => "search/tv",
        _ => "search/movie", // Default to movie
    };
    
    let request_url = format!("{}/{}", base_url, endpoint);
    println!("Fetching TMDB Metadata for: '{}' (Endpoint: {})", title, endpoint);

    let client = reqwest::Client::builder()
        .user_agent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
        .build()?;

    let resp = client.get(&request_url)
        .query(&[("api_key", api_key), ("query", title)])
        .send()
        .await?
        .json::<TmdbResponse>()
        .await?;

    if let Some(first_result) = resp.results.first() {
        let _year = first_result.date.as_ref()
            .map(|d| d.chars().take(4).collect::<String>())
            .unwrap_or_else(|| "0000".to_string());
        
        let _poster = first_result.poster_path.as_ref()
            .map(|p| format!("https://image.tmdb.org/t/p/w500{}", p))
            .unwrap_or_else(|| "N/A".to_string());

        let _backdrop = first_result.backdrop_path.as_ref()
            .map(|p| format!("https://image.tmdb.org/t/p/original{}", p));

        let _plot = first_result.overview.clone().unwrap_or_else(|| "No plot available".to_string());
        
        // Map TMDB types to our types
        let final_media_type = match media_type {
             Some("series") | Some("tv") => "series",
             _ => "movie"
        }.to_string();

        // Since search results don't have runtime/genres, we do a follow-up for the full details
        let full_meta = fetch_tmdb_by_id(first_result.id, Some(&final_media_type), api_key).await?;

        Ok(full_meta)
    } else {
        Err("No results found on TMDB".into())
    }
}

pub async fn fetch_season_details(tmdb_series_id: i64, season_number: i32, api_key: &str) -> Result<Vec<TmdbEpisode>, Box<dyn Error + Send + Sync>> {
    let url = format!("https://api.themoviedb.org/3/tv/{}/season/{}", tmdb_series_id, season_number);
    
    let client = build_client()?;
    let resp = client.get(&url)
        .query(&[("api_key", api_key)])
        .send().await?
        .json::<TmdbSeasonResponse>().await?;

    Ok(resp.episodes)
}
