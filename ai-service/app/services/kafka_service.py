"""Kafka producer/consumer cho AI Service.

- Producer: phát sự kiện sau khi /analyze thành công (không chặn HTTP nếu broker lỗi).
- Consumer group `ai-service`: tham gia cluster để trang Giám sát thấy group + lag.
Broker tắt → service vẫn chạy; health báo kafkaConnected=false.
"""

from __future__ import annotations

import asyncio
import json
import logging
from datetime import datetime, timezone
from typing import Any

from aiokafka import AIOKafkaConsumer, AIOKafkaProducer
from aiokafka.admin import AIOKafkaAdminClient, NewTopic
from aiokafka.errors import KafkaConnectionError, KafkaError

from app.core.config import get_settings
from app.models.schemas import AnalysisResponse

logger = logging.getLogger(__name__)

_producer: AIOKafkaProducer | None = None
_consumer: AIOKafkaConsumer | None = None
_consumer_task: asyncio.Task[None] | None = None
_connected = False


def is_kafka_connected() -> bool:
    return _connected


def kafka_bootstrap() -> str | None:
    settings = get_settings()
    if not settings.kafka_enabled:
        return None
    return settings.kafka_bootstrap_servers


async def start_kafka() -> None:
    """Khởi động producer + consumer; lỗi kết nối không làm sập AI service."""
    global _producer, _consumer, _consumer_task, _connected
    settings = get_settings()
    if not settings.kafka_enabled:
        logger.info("Kafka disabled (KAFKA_ENABLED=false)")
        _connected = False
        return

    bootstrap = settings.kafka_bootstrap_servers
    topic = settings.kafka_analysis_topic
    try:
        admin = AIOKafkaAdminClient(
            bootstrap_servers=bootstrap,
            client_id=f"{settings.kafka_client_id}-admin",
            request_timeout_ms=4000,
        )
        await admin.start()
        try:
            await admin.create_topics(
                [NewTopic(name=topic, num_partitions=3, replication_factor=1)],
                validate_only=False,
            )
        except Exception as exc:  # topic có thể đã tồn tại
            logger.debug("Kafka create_topics: %s", exc)
        finally:
            await admin.close()

        producer = AIOKafkaProducer(
            bootstrap_servers=bootstrap,
            client_id=settings.kafka_client_id,
            acks="all",
            request_timeout_ms=4000,
        )
        await producer.start()

        consumer = AIOKafkaConsumer(
            topic,
            bootstrap_servers=bootstrap,
            client_id=f"{settings.kafka_client_id}-consumer",
            group_id=settings.kafka_group_id,
            enable_auto_commit=True,
            auto_offset_reset="latest",
            request_timeout_ms=4000,
        )
        await consumer.start()

        _producer = producer
        _consumer = consumer
        _connected = True
        _consumer_task = asyncio.create_task(_consume_loop(consumer), name="ai-kafka-consumer")
        logger.info(
            "Kafka connected bootstrap=%s topic=%s group=%s",
            bootstrap,
            topic,
            settings.kafka_group_id,
        )
    except (KafkaConnectionError, KafkaError, OSError, asyncio.TimeoutError) as exc:
        _connected = False
        logger.warning("Kafka unavailable, AI continues without broker: %s", exc)
        await _shutdown_clients()
    except Exception as exc:
        _connected = False
        logger.warning("Kafka startup failed: %s", type(exc).__name__)
        await _shutdown_clients()


async def stop_kafka() -> None:
    global _consumer_task, _connected
    if _consumer_task and not _consumer_task.done():
        _consumer_task.cancel()
        try:
            await _consumer_task
        except asyncio.CancelledError:
            pass
    _consumer_task = None
    await _shutdown_clients()
    _connected = False


async def _shutdown_clients() -> None:
    global _producer, _consumer
    if _consumer is not None:
        try:
            await _consumer.stop()
        except Exception:
            pass
        _consumer = None
    if _producer is not None:
        try:
            await _producer.stop()
        except Exception:
            pass
        _producer = None


async def _consume_loop(consumer: AIOKafkaConsumer) -> None:
    """Giữ consumer group sống; log nhẹ để theo dõi trên dashboard Kafka."""
    try:
        async for message in consumer:
            try:
                payload = message.value.decode("utf-8") if message.value else ""
                logger.debug(
                    "Kafka ai-service consumed topic=%s partition=%s offset=%s bytes=%s",
                    message.topic,
                    message.partition,
                    message.offset,
                    len(payload),
                )
            except Exception as exc:
                logger.debug("Kafka message handle skipped: %s", type(exc).__name__)
    except asyncio.CancelledError:
        raise
    except Exception as exc:
        logger.warning("Kafka consumer stopped: %s", type(exc).__name__)
        global _connected
        _connected = False


async def publish_analysis_completed(result: AnalysisResponse) -> None:
    """Gửi event sau phân tích ảnh. Lỗi broker không làm fail HTTP response."""
    if not _connected or _producer is None:
        return
    settings = get_settings()
    top = result.top_prediction
    event: dict[str, Any] = {
        "event": "ai.analysis.completed",
        "service": "ai-service",
        "occurredAt": datetime.now(timezone.utc).isoformat(),
        "topClass": top.class_name if top else None,
        "confidence": top.confidence if top else None,
        "detectionCount": len(result.detections),
        "detectionFallback": result.detection_fallback,
        "embeddingGenerated": result.embedding_generated,
        "style": result.style_prediction.style if result.style_prediction else None,
        "colors": [c.name for c in result.dominant_colors[:5]],
        "inferenceTimeMs": result.inference_time_ms,
    }
    try:
        await _producer.send_and_wait(
            settings.kafka_analysis_topic,
            json.dumps(event, ensure_ascii=False).encode("utf-8"),
        )
    except Exception as exc:
        logger.warning("Kafka publish skipped: %s", type(exc).__name__)
