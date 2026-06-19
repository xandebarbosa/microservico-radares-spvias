package com.coruja.repository;

import com.coruja.entity.Radars;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class RadarsRepository implements PanacheMongoRepository<Radars> {

    /**
     * Busca paginada com filtro dinâmico usando documento BSON.
     * Equivalente ao JPA Specification / MongoTemplate do Spring.
     *
     * @param filter  Documento BSON com os critérios (pode ser vazio = sem filtro)
     * @param page    Número da página (0-indexed)
     * @param size    Tamanho da página
     * @return Lista de Radars correspondentes à página
     */
    public List<Radars> findWithFilterAndSort(Document filter, Document sort, int page, int size) {
        // Se nenhum critério de ordenação for fornecido, adota um fallback seguro
        Document sortDocument = (sort != null) ? sort : new Document("_id", -1);

        return mongoCollection().find(filter)
                .sort(sortDocument)
                .skip(page * size)
                .limit(size)
                .into(new ArrayList<>());
    }

    /**
     * Conta o total de documentos que correspondem ao filtro.
     * Necessário para montar o Page de resposta.
     */
    public long countWithFilter(Document filter) { return find(filter).count(); }

    /**
     * Busca por placa exata utilizando os recursos nativos de paginação do Panache.
     * 💡 Nota: Como o Panache por padrão ordena pelo id se não especificado, para consultas
     * de alta performance prefira usar o método 'findWithFilterAndSort' enviando o sort por DATA/HORA.
     */
    public List<Radars> findByPlaca(String placa, int page, int size) {
        return find("PLACA", placa.toUpperCase().trim())
                .page(Page.of(page, size))
                .list();
    }

    /**
     * Método que vai diretamente no MongoDB e traz os N registros mais novos.
     * 💡 Otimizado: O MongoDB aceita usar o índice composto parcial apenas para ordenação
     * se invertermos o sort para bater com as chaves cadastradas ({ DATA: -1, HORA: -1 }).
     */
    public List<Radars> findUltimos(int limit) {
        // Modificado para usar o índice composto existente, acelerando o dashboard em rempo real
        Document sortDocument = new Document("DATA", -1).append("HORA", -1);

        return mongoCollection().find()
                .sort(sortDocument)
                .limit(limit)
                .into(new ArrayList<>());
    }



}
