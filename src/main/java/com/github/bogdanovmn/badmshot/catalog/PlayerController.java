package com.github.bogdanovmn.badmshot.catalog;

import com.github.bogdanovmn.badmshot.archive.PlayerRenameInfo;
import com.github.bogdanovmn.badmshot.archive.PlayerRenameResult;
import com.github.bogdanovmn.badmshot.archive.PlayerRenameService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/players")
@RequiredArgsConstructor
class PlayerController {

    private final PlayerService playerService;
    private final PlayerRenameService playerRenameService;

    @PutMapping("/{id}")
    void update(@PathVariable("id") UUID id, @RequestBody PlayerUpdate request) {
        playerService.update(id, request);
    }

    @GetMapping("/{id}/rename-info")
    PlayerRenameInfo renameInfo(@PathVariable("id") UUID id) {
        return playerRenameService.info(id);
    }

    @PostMapping("/{id}/rename")
    PlayerRenameResult rename(@PathVariable("id") UUID id, @RequestBody PlayerRename request) {
        return playerRenameService.rename(id, request.getName());
    }
}
