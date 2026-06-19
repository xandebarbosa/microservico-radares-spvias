package com.coruja.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RadarLocationDTO {
    private Long id;
    private Double latitude;
    private Double longitude;
    private String concessionaria;
    private String rodovia;
    private String km;
    private String sentido;
    private String praca;
}
