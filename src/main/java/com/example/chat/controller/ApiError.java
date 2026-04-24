package com.example.chat.controller;

import java.util.Map;

public record ApiError(String message, Map<String, String> fieldErrors) {
}
