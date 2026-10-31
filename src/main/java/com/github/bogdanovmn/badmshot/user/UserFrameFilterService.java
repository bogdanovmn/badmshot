package com.github.bogdanovmn.badmshot.user;

import com.github.bogdanovmn.badmshot.catalog.ClubRepository;
import com.github.bogdanovmn.badmshot.catalog.PlayerRepository;
import com.github.bogdanovmn.badmshot.common.FrameFilterConstraints;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class UserFrameFilterService {

    private final UserFrameFilterRepository filterRepository;
    private final PlayerRepository playerRepository;
    private final ClubRepository clubRepository;
    private final FrameFilterConstraints filterConstraints;

    @Transactional(readOnly = true)
    UserFrameFilter get(UUID userId) {
        return filterRepository.find(userId).orElseGet(UserFrameFilter::empty);
    }

    @Transactional
    UserFrameFilter save(UUID userId, UserFrameFilterRequest request) {
        List<UUID> playerIds = playerRepository.canonicalIds(request.playersOrEmpty());
        filterConstraints.validate(playerIds, request.isTogether());
        filterRepository.save(
            userId,
            playerIds,
            clubRepository.existingIds(request.clubsOrEmpty()),
            request.isIncludeIndi(),
            request.isTogether()
        );
        return get(userId);
    }
}
