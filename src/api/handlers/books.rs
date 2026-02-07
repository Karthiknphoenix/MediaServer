use axum::{
    extract::{Path, State},
    response::{IntoResponse, Response},
    Json,
};
use axum::http::header;
use sqlx::SqlitePool;
use std::path::Path as StdPath;
use std::fs::File;
use std::io::Read;
use zip::ZipArchive;
use serde::Serialize;
use crate::error::AppError;


#[derive(Serialize)]
pub struct BookPage {
    pub index: usize,
    pub filename: String,
}

pub async fn get_book_pages(
    State(pool): State<SqlitePool>,
    Path(id): Path<i64>,
) -> Result<Json<Vec<BookPage>>, AppError> {
    let (file_path,): (String,) = sqlx::query_as("SELECT file_path FROM media WHERE id = ?")
        .bind(id)
        .fetch_one(&pool)
        .await
        .map_err(|_| AppError::NotFound("Media not found".into()))?;

    let path = StdPath::new(&file_path);
    let ext = path.extension().and_then(|e| e.to_str()).unwrap_or("").to_lowercase();

    if ["cbz", "zip", "cbx"].contains(&ext.as_str()) {
        let file = File::open(path).map_err(|e| AppError::Internal(format!("Could not open file: {}", e)))?;
        let mut archive = ZipArchive::new(file).map_err(|e| AppError::Internal(format!("Could not open zip archive: {}", e)))?;

        let mut pages = Vec::new();
        for i in 0..archive.len() {
            if let Ok(file) = archive.by_index(i) {
                let name: String = file.name().to_string();
                // Filter for images
                if is_image(&name) {
                    pages.push(BookPage {
                        index: i,
                        filename: name,
                    });
                }
            }
        }
        
        // Sort pages alphabetically for now
        pages.sort_by(|a, b| a.filename.to_lowercase().cmp(&b.filename.to_lowercase()));
        
        Ok(Json(pages))
    } else {
        // PDF/EPUB not supported for page listings yet
        Err(AppError::BadRequest("Page listing only supported for CBZ/ZIP/CBX".into()))
    }
}

pub async fn get_book_page(
    State(pool): State<SqlitePool>,
    Path((id, page_index)): Path<(i64, usize)>,
) -> Result<Response, AppError> {
    let result: Option<(String,)> = sqlx::query_as("SELECT file_path FROM media WHERE id = ?")
        .bind(id)
        .fetch_optional(&pool)
        .await
        .map_err(|e| AppError::Database(e))?;

    let (file_path,) = result.ok_or(AppError::NotFound("Media not found".into()))?;

    let path = StdPath::new(&file_path);
    let file = File::open(path).map_err(|e| AppError::Internal(format!("Could not open file: {}", e)))?;
    let mut archive = ZipArchive::new(file).map_err(|e| AppError::Internal(format!("Could not open zip archive: {}", e)))?;

    if page_index >= archive.len() {
        return Err(AppError::NotFound("Page not found".into()));
    }

    let mut zip_file = archive.by_index(page_index).map_err(|e| AppError::Internal(format!("Could not read page: {}", e)))?;
    
    let mut buffer = Vec::new();
    zip_file.read_to_end(&mut buffer).map_err(|e| AppError::Internal(format!("Could not read page content: {}", e)))?;

    let mime_type = mime_guess::from_path(zip_file.name()).first_or_octet_stream();

    Ok((
        [(header::CONTENT_TYPE, mime_type.as_ref())],
        buffer
    ).into_response())
}

fn is_image(filename: &str) -> bool {
    let lower = filename.to_lowercase();
    lower.ends_with(".jpg") || lower.ends_with(".jpeg") || lower.ends_with(".png") || lower.ends_with(".webp")
}
