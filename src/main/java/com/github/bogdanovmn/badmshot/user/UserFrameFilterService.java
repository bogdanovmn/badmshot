package com.github.bogdanovmn.badmshot.user;

import com.github.bogdanovmn.badmshot.catalog.ClubRepository;
import com.github.bogdanovmn.badmshot.catalog.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class UserFrameFilterService {

    private final UserFrameFilterRepository filterRepository;
    private final PlayerRepository playerRepository;
    private final ClubRepository clubRepository;

    @Transactional(readOnly = true)
    UserFrameFilter get(UUID userId) {
        return filterRepository.find(userId).orElseGet(UserFrameFilter::empty);
    }

    @Transactional
    UserFrameFilter save(UUID userId, UserFrameFilterRequest request) {
        filterRepository.save(
            userId,
            playerRepository.canonicalIds(request.playersOrEmpty()),
            clubRepository.existingIds(request.clubsOrEmpty()),
            request.isIncludeIndi()
        );
        return get(userId);
    }
}
