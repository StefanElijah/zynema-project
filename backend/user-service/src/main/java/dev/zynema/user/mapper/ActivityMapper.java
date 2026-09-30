package dev.zynema.user.mapper;

import dev.zynema.user.domain.WatchHistoryEntry;
import dev.zynema.user.domain.WatchlistItem;
import dev.zynema.user.dto.WatchHistoryDto;
import dev.zynema.user.dto.WatchlistItemDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ActivityMapper {

    WatchlistItemDto toDto(WatchlistItem item);

    List<WatchlistItemDto> toWatchlistDtoList(List<WatchlistItem> items);

    WatchHistoryDto toDto(WatchHistoryEntry entry);

    List<WatchHistoryDto> toHistoryDtoList(List<WatchHistoryEntry> entries);
}
