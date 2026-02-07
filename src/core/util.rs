use std::path::Path;
use tokio::fs;
use tokio::io::AsyncWriteExt;
use crate::error::AppError;

/// Save image bytes to the thumbnails directory with the given filename
pub async fn save_image(filename: &str, data: &[u8]) -> Result<String, AppError> {
    let thumb_dir = Path::new("static/thumbnails");
    if !thumb_dir.exists() {
        fs::create_dir_all(thumb_dir).await
            .map_err(|e| AppError::Internal(format!("Failed to create thumbnail dir: {}", e)))?;
    }

    let file_path = thumb_dir.join(filename);
    let mut file = fs::File::create(&file_path).await
        .map_err(|e| AppError::Internal(format!("Failed to create file: {}", e)))?;
    
    file.write_all(data).await
        .map_err(|e| AppError::Internal(format!("Failed to write file: {}", e)))?;

    // Return the relative URL path
    Ok(format!("/thumbnails/{}", filename))
}

pub fn sanitize_filename(name: &str) -> String {
    name.chars()
        .map(|c| if c.is_alphanumeric() || c == '-' || c == '_' { c } else { '_' })
        .collect()
}
