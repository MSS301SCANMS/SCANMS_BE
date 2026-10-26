package com.scanms.product.service.impl;

import com.scanms.product.dto.ShortVideoContracts.*;
import com.scanms.product.entity.*;
import com.scanms.product.repository.*;
import com.scanms.product.service.*;
import com.scanms.product.mapper.ShortVideoMapper;
import com.scanms.product.client.*;
import com.scanms.product.constant.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor
public class ShortVideoServiceImpl implements ShortVideoService {
    private final ShortVideoRepository videos;
    private final ProductRepository products;
    private final StoreRepository stores;
    private final ProductAccess access;
    private final ShortVideoAffiliateClient affiliate;
    private final ShortVideoUploadClient uploads;
    private final ShortVideoMapper mapper;

    @Override @Transactional
    public View create(Create input) {
        String user=access.userId();
        var previous=videos.findByPublicId(input.publicId());
        if(previous.isPresent()) {
            var saved=previous.get();
            var requested=input.products().stream().map(l->Map.of("productId",l.productId(),"referralLinkId",l.referralLinkId())).toList();
            if(!user.equals(saved.getCreatorUserId())||!input.title().trim().equals(saved.getTitle())||!Objects.equals(input.caption(),saved.getCaption())||!requested.equals(saved.getProductLinks()))
                throw error("Video tải lên đã được sử dụng. Vui lòng tải video mới.");
            return view(saved);
        }
        var ids=new HashSet<String>(); String storeId=null;
        for(var link:input.products()) {
            if(!ids.add(link.productId())) throw error("Mỗi sản phẩm chỉ được gắn một lần.");
            Product product=products.findById(link.productId()).orElseThrow(()->error("Không tìm thấy sản phẩm."));
            if(product.getStatus()!=ProductStatus.ACTIVE) throw error("Sản phẩm phải đang mở bán.");
            if(storeId==null) storeId=product.getStoreId();
            if(!storeId.equals(product.getStoreId())) throw error("Một video chỉ gắn sản phẩm của cùng một shop.");
        }
        String selectedStore=storeId;
        stores.findById(storeId).filter(s->s.getApprovalStatus()==StoreStatus.APPROVED).orElseThrow(()->error("Shop chưa được duyệt."));
        String bearer="Bearer "+access.bearerToken();
        Map<String,String> creator;
        Map<String,Object> asset;
        try {
            creator=affiliate.verify(bearer,storeId,input.products()).result();
            asset=uploads.verify(bearer,input.publicId()).result();
        } catch(feign.FeignException ex) {
            int code=ex.status();
            String message="Chưa xác minh được quyền KOL, hợp tác với shop hoặc video đã tải lên.";
            if(code==409) try {
                var body=tools.jackson.databind.json.JsonMapper.builder().build().readTree(ex.contentUTF8());
                if(body.path("message").isTextual()&&body.path("message").asText().length()<=500) message=body.path("message").asText();
            } catch(Exception ignored) { }
            throw new ResponseStatusException(code==400||code==403||code==409?HttpStatus.valueOf(code):HttpStatus.SERVICE_UNAVAILABLE,
                    message);
        }
        if(creator==null||!user.equals(creator.get("userId"))||asset==null) throw error("Không xác minh được chủ video.");
        var saved=videos.saveAndFlush(ShortVideo.builder().creatorUserId(user).collaboratorId(creator.get("collaboratorId"))
                .creatorName(creator.get("creatorName")).storeId(selectedStore).title(input.title().trim()).caption(input.caption())
                .publicId(input.publicId()).videoUrl(asset.get("secureUrl").toString()).posterUrl(Objects.toString(asset.get("posterUrl"),null))
                .durationSeconds(((Number)asset.get("durationSeconds")).doubleValue()).status("PUBLISHED")
                .productLinks(input.products().stream().map(l->Map.of("productId",l.productId(),"referralLinkId",l.referralLinkId())).toList()).build());
        return view(saved);
    }
    @Override @Transactional(readOnly=true)
    public Feed feed(int page) {
        var rows=videos.feed(PageRequest.of(page(page),12));
        return new Feed(rows.stream().map(this::view).filter(v->!v.products().isEmpty()).toList(),rows.hasNext()?page+1:null);
    }
    @Override @Transactional(readOnly=true)
    public View publicVideo(String id) {
        var v=videos.findById(id).filter(x->"PUBLISHED".equals(x.getStatus())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        stores.findById(v.getStoreId()).filter(s->s.getApprovalStatus()==StoreStatus.APPROVED).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        return view(v);
    }
    @Override @Transactional(readOnly=true)
    public Feed mine(int page) {
        var rows=videos.findByCreatorUserIdOrderByCreatedAtDesc(access.userId(),PageRequest.of(page(page),12));
        return new Feed(rows.stream().map(this::view).toList(),rows.hasNext()?page+1:null);
    }
    @Override @Transactional
    public void hide(String id) {
        var v=videos.lock(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!v.getCreatorUserId().equals(access.userId())&&!access.isManager()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        v.setStatus("HIDDEN");
    }
    private View view(ShortVideo v) {
        var cards=new ArrayList<ProductCard>();
        for(var link:v.getProductLinks()) products.findById(link.get("productId")).filter(p->p.getStatus()==ProductStatus.ACTIVE).ifPresent(p->{
            String image=null;
            if(p.getImageRefs()!=null) image=p.getImageRefs().values().stream().filter(String.class::isInstance).map(String.class::cast).filter(s->s.startsWith("https://")).findFirst().orElse(null);
            cards.add(new ProductCard(p.getProductId(),link.get("referralLinkId"),p.getName(),p.getPriceVnd(),image));
        });
        return mapper.view(v,cards);
    }
    private int page(int value) { if(value<0||value>10000) throw error("Trang không hợp lệ.");return value; }
    private ResponseStatusException error(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
