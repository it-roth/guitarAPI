package com.example.guitarapi.utils;

import com.example.guitarapi.models.ApiResponse;

public class ResponseUtil {
    public static ApiResponse success(Object data) {
        ApiResponse r = new ApiResponse();
        r.setStatus(new ApiResponse.Status(0, null, "Success"));
        r.setData(data);
        return r;
    }

    public static ApiResponse failure(int errorCode, String message) {
        ApiResponse r = new ApiResponse();
        r.setStatus(new ApiResponse.Status(1, errorCode, message));
        r.setData(null);
        return r;
    }
}
