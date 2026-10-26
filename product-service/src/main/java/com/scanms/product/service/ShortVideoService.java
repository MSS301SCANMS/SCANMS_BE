package com.scanms.product.service;

import com.scanms.product.dto.ShortVideoContracts.*;
public interface ShortVideoService {
    View create(Create input);
    Feed feed(int page);
    View publicVideo(String id);
    Feed mine(int page);
    void hide(String id);
}
