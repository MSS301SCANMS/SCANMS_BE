-- Preserve existing reviews while supporting the 1,000-character review form.
ALTER TABLE feedbacks ALTER COLUMN comment TYPE text;
