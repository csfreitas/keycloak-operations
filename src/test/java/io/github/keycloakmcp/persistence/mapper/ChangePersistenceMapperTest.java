package io.github.keycloakmcp.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.domain.change.ChangeOperation;
import io.github.keycloakmcp.domain.change.ChangeOperationType;

class ChangePersistenceMapperTest {

    private final ChangePersistenceMapper mapper = new ChangePersistenceMapper();

    @Test
    void preservesLegacyScalarOperationValues() {
        var json = mapper.fromOperations(List.of(
                new ChangeOperation("name", ChangeOperationType.UPDATE, "Old", "New")));

        assertThat(roundTrip(json).get(0).before()).isEqualTo("Old");
        assertThat(roundTrip(json).get(0).after()).isEqualTo("New");
    }

    @Test
    void preservesStructuredCollectionOperationValues() {
        var json = mapper.fromOperations(List.of(new ChangeOperation(
                "redirectUris",
                ChangeOperationType.UPDATE,
                List.of("https://old.example/callback"),
                List.of("https://new.example/callback"))));

        assertThat(roundTrip(json).get(0).before())
                .isEqualTo(List.of("https://old.example/callback"));
        assertThat(roundTrip(json).get(0).after())
                .isEqualTo(List.of("https://new.example/callback"));
    }

    private List<ChangeOperation> roundTrip(List<java.util.Map<String, Object>> json) {
        return mapper.toOperations(json);
    }
}
