package com.github.bogdanovmn.badmshot.infrastructure.video;

import com.github.bogdanovmn.badmshot.video.VideoProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class VideoConfiguration {

    @Bean
    VideoProbe videoProbe(@Value("${badmshot.video.ffprobe-path:ffprobe}") String ffprobePath) {
        return new VideoProbe(ffprobePath);
    }
}
