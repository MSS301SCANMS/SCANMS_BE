# Web chat attachments

Customer, approved KOL and store owner use the same existing conversation permissions.
The web composer now supports one image/document per message, an optional separate text
message, file preview/removal, upload progress, retry, image viewing and authenticated download.

## API and persistence

- `POST /api/v1/chat/conversations/{id}/attachments`: multipart `file` and UUID `messageId`.
  Returns the saved chat message in the usual `ApiResponse.result` envelope.
- `GET /api/v1/chat/conversations/{id}/attachments/{attachmentId}`: authenticated binary download.
  Both endpoints require membership in that conversation. The download has `no-store`,
  `nosniff` and an attachment content disposition; public URLs and query-string tokens are not used.
- `StoreChatAttachmentController` → `StoreChatAttachmentService` → implementation/repository.
  MapStruct builds the canonical attachment card. Existing message JSON carries only that
  metadata/reference, never the file bytes. Existing relay send/retry broadcasts the saved message.
- File bytes use PostgreSQL `bytea` in `store_chat_attachments` in Product DB. This avoids
  relying on one developer's disk or exposing private conversation files through public image uploads.
  The file and its message commit in one transaction, under the existing conversation lock.
  Reusing the same UUID and bytes returns the existing message. A conflicting UUID rolls back the upload.
- Maximum 10 MiB per file. Accepted: JPG/JPEG, PNG, WebP, PDF, UTF-8 TXT, DOCX and XLSX.
  Server checks file signatures, UTF-8 text or bounded Office ZIP content, ignores a forged client MIME,
  strips file paths/control characters, rejects Office macros and bounds decompression to 50 MiB/1000 entries.
  This is format validation, not antivirus scanning. Legacy DOC/XLS, arbitrary ZIP, executable files and video
  are outside this feature.
- Database byte storage is deliberately bounded for this project. Larger production volumes should use
  private object storage with the same authorization contract and a storage lifecycle policy.

## Run

1. Restart **product-service** to load the new endpoints/entity and 10 MiB multipart configuration.
2. With existing `JPA_DDL_AUTO=update`, Hibernate creates only the new table/index; catalog data is untouched.
   With `validate`, apply `product-service/src/main/resources/db/manual/20261105_store_chat_attachments.sql`
   to the correct Product DB first.
3. Reload FE. Gateway already forwards `/api/v1/chat/**` to Product; no new service or credentials.
   The existing realtime relay is used unchanged. Without it, the other participant sees saved messages
   through the existing history poll rather than a socket event.

## Validation

- `StoreChatAttachmentIntegrationTest` (5) + existing `StoreChatWebIntegrationTest` (7): 12 passing.
  Uses H2 PostgreSQL mode with a mocked identity boundary. Verifies real persistence/rollback,
  multipart/download HTTP, membership, retry, canonical metadata and forbidden/oversized types.
- FE production build and lint pass (pre-existing warnings remain).
- `tests/shop-chat-entry.browser.test.mjs`: real App/ShopPage → Chat ngay → image preview/send/view,
  PDF upload fail/retry with same UUID, private download, unsupported extension rejection,
  full-page chat and reload. Browser API responses are fixtures, not actual user DB accounts.
- Existing real relay browser integration is retained for structured/plain chat transport.
  No actual customer records, shared DB schema, external storage credentials or running IntelliJ
  processes are changed by these tests.
