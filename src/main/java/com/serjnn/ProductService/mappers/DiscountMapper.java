package com.serjnn.ProductService.mappers;

import com.serjnn.ProductService.dtos.DiscountResponseDto;
import com.serjnn.ProductService.dtos.DiscountChangesDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface DiscountMapper {
    DiscountMapper INSTANCE = Mappers.getMapper(DiscountMapper.class);

    @Mapping(source = "newDiscount", target = "discount")
    DiscountResponseDto toCacheableDto(DiscountChangesDto dto);


}
