package dev.zynema.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/** An episode of a season. {@code hlsPath} is filled by the video pipeline (Fase 6). */
@Entity
@Table(name = "episodes")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Episode {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @Column(name = "episode_number", nullable = false)
    private Integer episodeNumber;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String synopsis;

    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "still_url", length = 500)
    private String stillUrl;

    @Column(name = "hls_path", length = 500)
    private String hlsPath;
}
