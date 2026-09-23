package com.xdpsx.ecommerce.media.application;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xdpsx.ecommerce.media.api.dto.ViewMediaDTO;
import com.xdpsx.ecommerce.media.domain.Media;

@Mapper
public interface MediaMapper {
    MediaMapper INSTANCE = Mappers.getMapper(MediaMapper.class);

    ViewMediaDTO toViewMediaDTO(Media media);
}
