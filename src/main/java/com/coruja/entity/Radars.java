package com.coruja.entity;

import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.*;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

@MongoEntity(collection = "Spvias", database = "Veiculos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Radars {
    @BsonId
    public ObjectId id;

    @BsonProperty("DATA")
    public String data;

    @BsonProperty("HORA")
    public String hora;

    @BsonProperty("PLACA")
    public String placa;

    @BsonProperty("LOCAL")
    public String Local;

    @BsonProperty("SENTIDO")
    public String sentido;

    /** * Campo fixo para a Concessionária.
     * @BsonIgnore garante que o Mongo não tente ler/gravar essa informação.
     */
    @BsonIgnore
    public String concessionaria = "SPVias";
}
