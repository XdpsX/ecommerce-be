package com.xdpsx.ecommerce.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xdpsx.ecommerce.dtos.media.ViewMediaDTO;
import com.xdpsx.ecommerce.entities.Media;

@Mapper
public interface MediaMapper {
    MediaMapper INSTANCE = Mappers.getMapper(MediaMapper.class);

    ViewMediaDTO toViewMediaDTO(Media media);
}
