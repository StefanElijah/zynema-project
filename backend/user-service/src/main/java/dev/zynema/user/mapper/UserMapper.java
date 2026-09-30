package dev.zynema.user.mapper;

import dev.zynema.user.domain.Profile;
import dev.zynema.user.domain.User;
import dev.zynema.user.dto.ProfileDto;
import dev.zynema.user.dto.UserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "profiles", source = "profiles")
    UserDto toDto(User user);

    ProfileDto toDto(Profile profile);

    List<ProfileDto> toProfileDtoList(List<Profile> profiles);
}
