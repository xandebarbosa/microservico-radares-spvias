package com.coruja.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RadarsDTO {

    private Long id;
    private LocalDate data;
    private LocalTime hora;
    private String placa;
    private String praca;
    private String sentido;
    private String concessionaria;

    private Double latitude;
    private Double longitude;
}
