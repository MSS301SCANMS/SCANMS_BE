package com.scanms.product.service;

import com.scanms.product.exception.AppException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeedbackTextPolicyTest {
    @Test
    void rejectsExplicitProfanityIncludingSeparatedLettersAndInvisibleCharacters() throws Exception {
        var policy = new FeedbackTextPolicy();
        for (String comment : new String[]{"địt mẹ", "f.u.c.k", "F U C K", "sh\u200bit", "con cặc"})
            assertThrows(AppException.class, () -> policy.validate(comment), comment);
    }

    @Test
    void permitsNegativeReviewsOrdinaryVietnameseWordsAndPartialEnglishMatches() throws Exception {
        var policy = new FeedbackTextPolicy();
        for (String comment : new String[]{"Áo quá lớn, giao hàng chậm, chất lượng rất tệ", "Mũ không đúng mô tả, tôi cho 1 sao", "Mushroom shiitake", "Sản phẩm đẹp", ""})
            assertDoesNotThrow(() -> policy.validate(comment), comment);
    }
}
