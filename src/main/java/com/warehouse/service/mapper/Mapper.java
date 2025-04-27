package com.warehouse.service.mapper;

import java.util.List;

public interface Mapper<Entity, DTO> {
    DTO toDTO(Entity entity);
    Entity toEntity(DTO dto);
    List<DTO> toDTOList(List<Entity> entities);
    List<Entity> toEntityList(List<DTO> dtos);
}

