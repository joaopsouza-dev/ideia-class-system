package com.catolica.ideiaspace.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LogAcessoId implements Serializable {

    private Long id;
    private LocalDateTime criadoEm;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof LogAcessoId other)) {
            return false;
        }

        return Objects.equals(id, other.id)
                && Objects.equals(criadoEm, other.criadoEm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, criadoEm);
    }
}