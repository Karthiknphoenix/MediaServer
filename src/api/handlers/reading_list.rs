use axum::{
    extract::{Path, State},
    Json,
};
use serde::{Deserialize, Serialize};
use sqlx::SqlitePool;
use crate::error::AppError;

// --- DTOs ---

#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct ReadingList {
    pub id: i64,
    pub name: String,
}

#[derive(Debug, Deserialize)]
pub struct CreateReadingListRequest {
    pub name: String,
}

#[derive(Debug, Deserialize)]
pub struct AddItemsRequest {
    pub media_ids: Vec<i64>,
}

#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct ReadingListItem {
    pub id: i64,
    pub list_id: i64,
    pub media_id: i64,
    pub position: i64,
    // Joined fields from media table
    pub title: Option<String>,
    pub poster_url: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct ReadingListWithItems {
    pub id: i64,
    pub name: String,
    pub items: Vec<ReadingListItem>,
}

// --- Handlers ---

/// Get all reading lists
pub async fn get_all_lists(
    State(pool): State<SqlitePool>,
) -> Result<Json<Vec<ReadingList>>, AppError> {
    let lists: Vec<ReadingList> = sqlx::query_as(
        "SELECT id, name FROM reading_lists ORDER BY id DESC"
    )
    .fetch_all(&pool)
    .await?;
    
    Ok(Json(lists))
}

/// Create a new reading list
pub async fn create_list(
    State(pool): State<SqlitePool>,
    Json(payload): Json<CreateReadingListRequest>,
) -> Result<Json<ReadingList>, AppError> {
    let result = sqlx::query("INSERT INTO reading_lists (name) VALUES (?)")
        .bind(&payload.name)
        .execute(&pool)
        .await?;
    
    let id = result.last_insert_rowid();
    
    Ok(Json(ReadingList {
        id,
        name: payload.name,
    }))
}

/// Get a reading list with its items
pub async fn get_list_details(
    Path(list_id): Path<i64>,
    State(pool): State<SqlitePool>,
) -> Result<Json<ReadingListWithItems>, AppError> {
    // Get list metadata
    let list: ReadingList = sqlx::query_as(
        "SELECT id, name FROM reading_lists WHERE id = ?"
    )
    .bind(list_id)
    .fetch_optional(&pool)
    .await?
    .ok_or_else(|| AppError::NotFound("Reading list not found".into()))?;
    
    // Get items with media info
    let items: Vec<ReadingListItem> = sqlx::query_as(
        r#"
        SELECT 
            rli.id, rli.list_id, rli.media_id, rli.position,
            m.title, m.poster_url
        FROM reading_list_items rli
        LEFT JOIN media m ON rli.media_id = m.id
        WHERE rli.list_id = ?
        ORDER BY rli.position ASC
        "#
    )
    .bind(list_id)
    .fetch_all(&pool)
    .await?;
    
    Ok(Json(ReadingListWithItems {
        id: list.id,
        name: list.name,
        items,
    }))
}

/// Add items to a reading list
pub async fn add_items_to_list(
    Path(list_id): Path<i64>,
    State(pool): State<SqlitePool>,
    Json(payload): Json<AddItemsRequest>,
) -> Result<Json<()>, AppError> {
    // Get current max position
    let max_pos: Option<(i64,)> = sqlx::query_as(
        "SELECT MAX(position) FROM reading_list_items WHERE list_id = ?"
    )
    .bind(list_id)
    .fetch_optional(&pool)
    .await?;
    
    let mut current_pos = max_pos.and_then(|r| Some(r.0)).unwrap_or(0);
    
    for media_id in payload.media_ids {
        current_pos += 1;
        sqlx::query(
            "INSERT INTO reading_list_items (list_id, media_id, position) VALUES (?, ?, ?)"
        )
        .bind(list_id)
        .bind(media_id)
        .bind(current_pos)
        .execute(&pool)
        .await?;
    }
    
    Ok(Json(()))
}

/// Delete a reading list
pub async fn delete_list(
    Path(list_id): Path<i64>,
    State(pool): State<SqlitePool>,
) -> Result<Json<()>, AppError> {
    // Delete items first
    sqlx::query("DELETE FROM reading_list_items WHERE list_id = ?")
        .bind(list_id)
        .execute(&pool)
        .await?;
    
    // Delete list
    sqlx::query("DELETE FROM reading_lists WHERE id = ?")
        .bind(list_id)
        .execute(&pool)
        .await?;
    
    Ok(Json(()))
}

/// Remove an item from a reading list
pub async fn remove_item_from_list(
    Path((list_id, item_id)): Path<(i64, i64)>,
    State(pool): State<SqlitePool>,
) -> Result<Json<()>, AppError> {
    sqlx::query("DELETE FROM reading_list_items WHERE list_id = ? AND id = ?")
        .bind(list_id)
        .bind(item_id)
        .execute(&pool)
        .await?;
    
    Ok(Json(()))
}
