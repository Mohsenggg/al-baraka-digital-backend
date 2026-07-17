package com.mgh.backend.product.service;

import com.mgh.backend.product.dto.request.CreateReferenceDataRequest;
import com.mgh.backend.product.dto.response.ReferenceItemDto;

import java.util.List;

public interface ProductLookupService {
    List<ReferenceItemDto> getCategories();
    ReferenceItemDto createCategory(CreateReferenceDataRequest request);

    List<ReferenceItemDto> getManufacturers();
    ReferenceItemDto createManufacturer(CreateReferenceDataRequest request);

    List<ReferenceItemDto> getSuppliers();
    ReferenceItemDto createSupplier(CreateReferenceDataRequest request);

    List<ReferenceItemDto> getAttributes();
    ReferenceItemDto createAttribute(CreateReferenceDataRequest request);
}
