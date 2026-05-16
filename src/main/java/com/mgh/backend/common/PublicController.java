package com.mgh.backend.common;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@CrossOrigin(origins = "http://localhost:4200") // should be removed and configured with the filter chain
@RequiredArgsConstructor
public class PublicController {


    @GetMapping("/welcome")
    public ResponseEntity<String> getGreet() {
        return ResponseEntity.ok("Welcome");
    }
}
