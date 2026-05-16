package com.mgh.backend.common.lookup.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "governorate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Governorate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_ar", nullable = false)
    private String nameAr;

    @OneToMany(mappedBy = "governorate", fetch = FetchType.LAZY)
    private List<City> cities = new ArrayList<>();
}
