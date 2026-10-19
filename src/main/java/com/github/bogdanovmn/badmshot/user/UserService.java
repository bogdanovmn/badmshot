package com.github.bogdanovmn.badmshot.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class UserService {

    private final UserRepository userRepository;

    @Transactional
    public void register(UUID userId, String displayName) {
        userRepository.getOrCreate(userId, displayName);
    }
}
