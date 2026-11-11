package com.jaguata.plataforma.controller;

import com.jaguata.plataforma.domain.enums.StatusMatch;
import com.jaguata.plataforma.domain.enums.StatusVaga;
import com.jaguata.plataforma.dto.Responses.AdminOngResponse;
import com.jaguata.plataforma.dto.Responses.ResumoResponse;
import com.jaguata.plataforma.repository.*;
import com.jaguata.plataforma.service.OngService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Área administrativa: todas as rotas exigem papel ADMIN. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final OngService ongs;
    private final OngRepository ongRepo;
    private final EmpresaRepository empresaRepo;
    private final CandidatoRepository candidatoRepo;
    private final VagaRepository vagaRepo;
    private final MatchRepository matchRepo;

    public AdminController(OngService ongs, OngRepository ongRepo, EmpresaRepository empresaRepo,
                           CandidatoRepository candidatoRepo, VagaRepository vagaRepo, MatchRepository matchRepo) {
        this.ongs = ongs;
        this.ongRepo = ongRepo;
        this.empresaRepo = empresaRepo;
        this.candidatoRepo = candidatoRepo;
        this.vagaRepo = vagaRepo;
        this.matchRepo = matchRepo;
    }

    /** ?verificada=false lista as pendentes; sem parâmetro lista todas. */
    @GetMapping("/ongs")
    public List<AdminOngResponse> listarOngs(@RequestParam(required = false) Boolean verificada) {
        return ongs.listar(verificada).stream().map(AdminOngResponse::from).toList();
    }

    @PostMapping("/ongs/{id}/verificar")
    public AdminOngResponse verificar(@PathVariable UUID id) {
        return AdminOngResponse.from(ongs.verificar(id));
    }

    @PostMapping("/ongs/{id}/revogar")
    public AdminOngResponse revogar(@PathVariable UUID id) {
        return AdminOngResponse.from(ongs.revogarVerificacao(id));
    }

    @GetMapping("/resumo")
    @Transactional(readOnly = true)
    public ResumoResponse resumo() {
        Map<StatusMatch, Long> porStatus = new EnumMap<>(StatusMatch.class);
        for (StatusMatch s : StatusMatch.values()) porStatus.put(s, matchRepo.countByStatus(s));
        long verificadas = ongRepo.countByVerified(true);
        return new ResumoResponse(empresaRepo.count(), ongRepo.count(), verificadas, ongRepo.count() - verificadas,
                candidatoRepo.count(), vagaRepo.countByStatus(StatusVaga.ABERTA), porStatus);
    }
}
