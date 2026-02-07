use axum::{
    extract::{Path, State},
    Json,
};
use sqlx::{SqlitePool, FromRow};
use serde::Serialize;
use crate::error::AppError;

#[derive(Debug, Serialize, FromRow)]
pub struct ComicSeries {
    pub name: String,
    pub chapter_count: i64,
    pub poster_url: Option<String>,
    pub backdrop_url: Option<String>,
    pub plot: Option<String>,
    pub year: Option<i64>,
    pub genres: Option<String>,
}

#[derive(Debug, Serialize, FromRow)]
pub struct ComicChapter {
    pub id: i64,
    pub title: Option<String>,
    pub chapter_number: Option<i32>,
    pub poster_url: Option<String>,
    pub file_path: String,
    pub plot: Option<String>,
    pub year: Option<i64>,
}

#[derive(Debug, serde::Deserialize)]
pub struct UpdateSeriesMetadataRequest {
    pub poster_url: Option<String>,
    pub backdrop_url: Option<String>,
    pub plot: Option<String>,
    pub year: Option<i64>,
    pub genres: Option<String>,
}

/// Get all comic series (grouped by series_name)
pub async fn get_comic_series(
    State(pool): State<SqlitePool>,
) -> Result<Json<Vec<ComicSeries>>, AppError> {
    let series: Vec<ComicSeries> = sqlx::query_as(
        r#"
        SELECT 
            series_name as name,
            COUNT(*) as chapter_count,
            (SELECT m2.poster_url FROM media m2 
             WHERE m2.series_name = m.series_name AND m2.media_type = 'book' 
             ORDER BY m2.episode_number ASC LIMIT 1) as poster_url,
            (SELECT m2.backdrop_url FROM media m2 
             WHERE m2.series_name = m.series_name AND m2.media_type = 'book' AND m2.backdrop_url IS NOT NULL
             ORDER BY m2.episode_number ASC LIMIT 1) as backdrop_url,
            (SELECT m2.plot FROM media m2 
             WHERE m2.series_name = m.series_name AND m2.media_type = 'book' AND m2.plot IS NOT NULL
             ORDER BY m2.episode_number ASC LIMIT 1) as plot,
            MAX(year) as year,
            GROUP_CONCAT(DISTINCT genres) as genres
        FROM media m
        WHERE m.media_type = 'book' AND m.series_name IS NOT NULL
        GROUP BY series_name
        ORDER BY series_name
        "#
    )
    .fetch_all(&pool)
    .await?;

    Ok(Json(series))
}

/// Get chapters for a specific comic series
pub async fn get_comic_chapters(
    Path(series_name): Path<String>,
    State(pool): State<SqlitePool>,
) -> Result<Json<Vec<ComicChapter>>, AppError> {
    let chapters: Vec<ComicChapter> = sqlx::query_as(
        r#"
        SELECT 
            id,
            title,
            episode_number as chapter_number,
            poster_url,
            file_path,
            plot,
            year
        FROM media
        WHERE media_type = 'book' AND series_name = ?
        ORDER BY episode_number ASC, title ASC
        "#
    )
    .bind(&series_name)
    .fetch_all(&pool)
    .await?;

    Ok(Json(chapters))
}

/// Get detailed info about a comic series
pub async fn get_comic_series_detail(
    Path(series_name): Path<String>,
    State(pool): State<SqlitePool>,
) -> Result<Json<serde_json::Value>, AppError> {
    let chapters: Vec<ComicChapter> = sqlx::query_as(
        r#"
        SELECT 
            id,
            title,
            episode_number as chapter_number,
            poster_url,
            file_path,
            plot,
            year
        FROM media
        WHERE media_type = 'book' AND series_name = ?
        ORDER BY episode_number ASC, title ASC
        "#
    )
    .bind(&series_name)
    .fetch_all(&pool)
    .await?;

    // Aggregate series metadata from chapters (or use the first/most complete one)
    let poster_url = chapters.first().and_then(|c| c.poster_url.clone());
    let plot = chapters.iter().find_map(|c| c.plot.clone());
    let year = chapters.iter().find_map(|c| c.year);
    
    // Also fetch backdrop and genres specifically for the series if possible
    let extra_meta: Option<(Option<String>, Option<String>)> = sqlx::query_as(
        "SELECT backdrop_url, genres FROM media WHERE series_name = ? AND media_type = 'book' AND backdrop_url IS NOT NULL LIMIT 1"
    )
    .bind(&series_name)
    .fetch_optional(&pool)
    .await
    .unwrap_or(None);

    let (backdrop_url, genres) = extra_meta.unwrap_or((None, None));

    Ok(Json(serde_json::json!({
        "name": series_name,
        "chapter_count": chapters.len(),
        "poster_url": poster_url,
        "backdrop_url": backdrop_url,
        "plot": plot,
        "year": year,
        "genres": genres,
        "chapters": chapters
    })))
}

/// Update metadata for a comic series
pub async fn update_comic_series_metadata(
    Path(series_name): Path<String>,
    State(pool): State<SqlitePool>,
    mut multipart: axum::extract::Multipart,
) -> Result<Json<()>, AppError> {
    
    let mut plot = None;
    let mut year = None;
    let mut genres = None;
    let mut poster_url = None;
    let mut backdrop_url = None;

    while let Some(field) = multipart.next_field().await.map_err(|e| AppError::BadRequest(e.to_string()))? {
        let name = field.name().unwrap_or("").to_string();
        
        match name.as_str() {
            "plot" => {
                if let Ok(val) = field.text().await {
                    if !val.is_empty() { plot = Some(val); }
                }
            },
            "year" => {
                if let Ok(val) = field.text().await {
                   if let Ok(parsed) = val.parse::<i64>() { year = Some(parsed); }
                }
            },
            "genres" => {
                if let Ok(val) = field.text().await {
                    if !val.is_empty() { genres = Some(val); }
                }
            },
            "backdrop_url" => {
                 if let Ok(val) = field.text().await {
                    if !val.is_empty() { backdrop_url = Some(val); }
                }
            },
            "poster" => {
                // If it's a file upload
                if let Some(filename) = field.file_name().map(|s| s.to_string()) {
                     if let Ok(bytes) = field.bytes().await {
                        // Generate safe filename: series_name_timestamp.ext
                        // Or just series_name_sanitized.jpg (overwriting previous)
                        // Overwriting is cleaner for storage management here.
                        let ext = std::path::Path::new(&filename)
                            .extension()
                            .and_then(std::ffi::OsStr::to_str)
                            .unwrap_or("jpg");
                            
                        let safe_series_name = crate::core::util::sanitize_filename(&series_name);
                        let saved_filename = format!("comic_series_{}.{}", safe_series_name, ext);
                        
                        let saved_path = crate::core::util::save_image(&saved_filename, &bytes).await?;
                        poster_url = Some(saved_path);
                     }
                }
            },
            // Fallback for poster_url as text (if not uploading file)
             "poster_url" => {
                 if let Ok(val) = field.text().await {
                    if !val.is_empty() { poster_url = Some(val); }
                }
            },
            _ => { 
                // Ignore unknown fields
                let _ = field.bytes().await; 
            }
        }
    }

    // Update DB
    let mut updates = Vec::new();
    let mut params = Vec::new();

    if let Some(p) = plot {
        updates.push("plot = ?");
        params.push(p);
    }
    if let Some(y) = year {
        updates.push("year = ?");
        params.push(y.to_string());
    }
    if let Some(g) = genres {
        updates.push("genres = ?");
        params.push(g);
    }
    if let Some(p) = poster_url {
        updates.push("poster_url = ?");
        params.push(p);
    }
    if let Some(b) = backdrop_url {
        updates.push("backdrop_url = ?");
        params.push(b);
    }

    if updates.is_empty() {
        return Ok(Json(()));
    }

    let query_str = format!("UPDATE media SET {} WHERE series_name = ? AND media_type = 'book'", updates.join(", "));
    
    // Manual binding loop because sqlx macro doesn't like dynamic query strings easily
    let mut query = sqlx::query(&query_str);
    for param in params {
        query = query.bind(param);
    }
    query = query.bind(series_name);
    
    query.execute(&pool).await?;

    Ok(Json(()))
}
