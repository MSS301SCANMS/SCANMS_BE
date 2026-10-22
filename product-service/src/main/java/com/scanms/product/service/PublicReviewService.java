package com.scanms.product.service;

import com.scanms.product.constant.FeedbackStatus;
import com.scanms.product.entity.Feedback;
import com.scanms.product.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class PublicReviewService {
    private final FeedbackRepository feedbacks;
    public Map<String,Object> summary(String productId) {
        var active=feedbacks.findByProductIdInAndStatus(List.of(productId),FeedbackStatus.ACTIVE);
        var distribution=new TreeMap<Integer,Long>();for(int star=1;star<=5;star++)distribution.put(star,0L);
        active.forEach(f->distribution.computeIfPresent(f.getRating(),(k,v)->v+1));
        var items=active.stream().sorted(Comparator.comparing(Feedback::getCreatedAt,Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20).map(f->{var row=new LinkedHashMap<String,Object>();
                    row.put("id",f.getFeedbackId());row.put("customerName","Khách đã mua hàng");row.put("rating",f.getRating());
                    row.put("comment",f.getComment());row.put("createdAt",f.getCreatedAt());row.put("isVerifiedBuyer",true);
                    row.put("reviewImageUrl",f.getImageRefs()==null||f.getImageRefs().isEmpty()?null:f.getImageRefs().getFirst());
                    return row;}).toList();
        var result=new LinkedHashMap<String,Object>();
        result.put("averageRating",active.isEmpty()?null:BigDecimal.valueOf(active.stream().mapToInt(Feedback::getRating).sum()).divide(BigDecimal.valueOf(active.size()),2,RoundingMode.HALF_UP));
        result.put("totalReviews",active.size());result.put("starDistribution",distribution);result.put("items",items);return result;
    }
}
