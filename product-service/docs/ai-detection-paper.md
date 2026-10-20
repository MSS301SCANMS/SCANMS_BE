# SCANMS Product AI Detection (paper stub)

## Mục tiêu
Hỗ trợ shop khi tạo sản phẩm: nhận diện loại hàng, màu chủ đạo và phong cách từ ảnh bìa, giảm thời gian nhập liệu thủ công.

## Pipeline
1. FE upload ảnh → `POST /api/v1/products/ai-analyze` (multipart `image`).
2. `product-service` ủy quyền tới `ai-service` (`/api/v1/ai/analyze`).
3. Kết quả: detections, topPrediction, dominantColors, stylePrediction, inferenceTimeMs.

## Đánh giá (stub)
- Dataset: ảnh sản phẩm thời trang nội bộ (placeholder).
- Metric: top-1 class accuracy, màu chủ đạo khớp nhãn tay.
- Latency mục tiêu: < 2s/ảnh trên môi trường lab.

## Giới hạn
- Không thay thế kiểm duyệt thủ công.
- Fallback khi AI service unavailable → FE giữ form thủ công.
