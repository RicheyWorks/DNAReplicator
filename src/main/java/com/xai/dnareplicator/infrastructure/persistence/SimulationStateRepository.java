package com.xai.dnareplicator.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.xai.dnareplicator.config.Config;
import com.xai.dnareplicator.config.SimulationProperties;
import com.xai.dnareplicator.controller.InfectionEngine;
import com.xai.dnareplicator.controller.ProteinService;
import com.xai.dnareplicator.model.DNAFragment;
import com.xai.dnareplicator.model.Protein;
import com.xai.dnareplicator.model.SimulationSession;
import com.xai.dnareplicator.model.Virus;
import com.xai.dnareplicator.presentation.contract.SimulationViewPort;
import com.xai.dnareplicator.service.DNAService;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves and loads simulation snapshots as versioned JSON.
 */
@Component
public class SimulationStateRepository {

    private final ObjectMapper objectMapper;
    private final SimulationProperties properties;

    public SimulationStateRepository(ObjectMapper objectMapper, SimulationProperties properties) {
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.properties = properties;
    }

    public File getStateFile() {
        String path = properties.getDna().getStateFilePath();
        if (!path.endsWith(".json")) {
            path = path.replace(".vrs", ".json");
            if (!path.endsWith(".json")) {
                path = path + ".json";
            }
        }
        return new File(path);
    }

    public void save(
            DNAService dnaService,
            InfectionEngine infectionEngine,
            double mutationRate) throws IOException {
        SimulationStateDto dto = new SimulationStateDto();
        dto.setMutationRate(mutationRate);
        dto.setLevel(infectionEngine.getLevel().getLevel());
        dto.setInfectedCells(infectionEngine.getVirologyModel().getInfectedCells());
        dto.setResistantCells(infectionEngine.getVirologyModel().getResistantCells());
        dto.setInfectionHistory(new ArrayList<>(infectionEngine.getVirologyModel().getInfectionHistory()));

        for (DNAFragment fragment : dnaService.getDNAFragments()) {
            SimulationStateDto.DnaFragmentDto f = new SimulationStateDto.DnaFragmentDto();
            f.setX(fragment.getX());
            f.setY(fragment.getY());
            f.setName(fragment.getName());
            f.setSelected(fragment.isSelected());
            f.setBasePairs(fragment.getBasePairs());
            dto.getDnaFragments().add(f);
        }

        for (Protein protein : dnaService.getProteins()) {
            SimulationStateDto.ProteinDto p = new SimulationStateDto.ProteinDto();
            p.setX(protein.getX());
            p.setY(protein.getY());
            p.setFolded(protein.isFolded());
            p.setFoldFailed(protein.isFoldFailed());
            p.setEnzymeType(protein.getEnzymeType());
            p.setViralResistance(protein.getViralResistance());
            dto.getProteins().add(p);
        }

        Virus virus = infectionEngine.getVirus();
        if (virus != null) {
            SimulationStateDto.VirusDto v = new SimulationStateDto.VirusDto();
            v.setX(virus.getX());
            v.setY(virus.getY());
            v.setResistanceFactor(virus.getResistanceFactor());
            v.setName(virus.getName());
            v.setInfectionEfficiency(virus.getInfectionEfficiency());
            dto.setVirus(v);
        }

        File file = getStateFile();
        file.getParentFile().mkdirs();
        objectMapper.writeValue(file, dto);
    }

    public LoadedSimulation load(
            SimulationSession session,
            SimulationViewPort viewPort,
            DNAService dnaService,
            InfectionEngine infectionEngine,
            ProteinService proteinService) throws IOException {
        File jsonFile = getStateFile();
        SimulationStateDto dto;
        if (jsonFile.exists()) {
            dto = objectMapper.readValue(jsonFile, SimulationStateDto.class);
        } else {
            dto = tryLoadLegacyVrs();
            if (dto == null) {
                throw new IOException("No save file found at " + jsonFile.getPath());
            }
        }

        session.clearDnaAndProteins();
        viewPort.clearAll();

        for (SimulationStateDto.DnaFragmentDto f : dto.getDnaFragments()) {
            DNAFragment fragment = new DNAFragment(f.getX(), f.getY(), f.getName());
            fragment.setSelected(f.isSelected());
            fragment.setBasePairs(f.getBasePairs());
            session.getDnaFragments().add(fragment);
            viewPort.addDNAFragment(fragment, f.getX(), f.getY(), f.getBasePairs(), f.isSelected(),
                    () -> dnaService.toggleSelection(fragment),
                    () -> dnaService.updateFragmentPosition(fragment));
        }

        for (SimulationStateDto.ProteinDto p : dto.getProteins()) {
            Protein protein = new Protein(p.getX(), p.getY(), p.getEnzymeType());
            if (p.isFolded()) {
                protein.fold();
            }
            if (p.isFoldFailed()) {
                protein.failFold();
            }
            session.getProteins().add(protein);
            viewPort.addProtein(protein, p.getX(), p.getY(), p.isFolded(), p.isFoldFailed(),
                    p.getEnzymeType(), p.getViralResistance());
        }

        if (dto.getVirus() != null) {
            infectionEngine.buildVirus(session.getProteins(), proteinService);
            Virus virus = infectionEngine.getVirus();
            if (virus != null) {
                virus.move(dto.getVirus().getX() - virus.getX(), dto.getVirus().getY() - virus.getY());
                viewPort.addVirus(virus, dto.getVirus().getX(), dto.getVirus().getY());
                viewPort.updateVirusInfo(dto.getVirus().getName(), dto.getVirus().getResistanceFactor());
            }
        }

        infectionEngine.getLevel().setLevel(dto.getLevel());
        infectionEngine.getVirologyModel().setInfectedCells(dto.getInfectedCells());
        infectionEngine.getVirologyModel().setResistantCells(dto.getResistantCells());
        infectionEngine.getVirologyModel().setInfectionHistory(dto.getInfectionHistory());

        viewPort.updateLevel(dto.getLevel());
        viewPort.updateVirology(dto.getInfectedCells(), dto.getResistantCells());
        viewPort.updateInfectionHistory(dto.getInfectionHistory());

        return new LoadedSimulation(dto.getMutationRate());
    }

    private SimulationStateDto tryLoadLegacyVrs() throws IOException {
        File legacy = new File(Config.getStateFilePath());
        if (!legacy.exists()) {
            return null;
        }
        SimulationStateDto dto = new SimulationStateDto();
        try (BufferedReader reader = new BufferedReader(new FileReader(legacy))) {
            String line = reader.readLine();
            int dnaCount = Integer.parseInt(line.split(": ")[1]);
            for (int i = 0; i < dnaCount; i++) {
                line = reader.readLine();
                String[] parts = line.split(",", 5);
                SimulationStateDto.DnaFragmentDto f = new SimulationStateDto.DnaFragmentDto();
                f.setX(Double.parseDouble(parts[0]));
                f.setY(Double.parseDouble(parts[1]));
                f.setName(parts[2]);
                f.setSelected(Boolean.parseBoolean(parts[3]));
                f.setBasePairs(parts[4]);
                dto.getDnaFragments().add(f);
            }
            line = reader.readLine();
            int proteinCount = Integer.parseInt(line.split(": ")[1]);
            for (int i = 0; i < proteinCount; i++) {
                line = reader.readLine();
                String[] parts = line.split(",");
                SimulationStateDto.ProteinDto p = new SimulationStateDto.ProteinDto();
                p.setX(Double.parseDouble(parts[0]));
                p.setY(Double.parseDouble(parts[1]));
                p.setFolded(Boolean.parseBoolean(parts[2]));
                p.setFoldFailed(Boolean.parseBoolean(parts[3]));
                p.setEnzymeType(parts[4]);
                p.setViralResistance(Double.parseDouble(parts[5]));
                dto.getProteins().add(p);
            }
            line = reader.readLine();
            if (Boolean.parseBoolean(line.split(": ")[1])) {
                line = reader.readLine();
                String[] parts = line.split(",");
                SimulationStateDto.VirusDto v = new SimulationStateDto.VirusDto();
                v.setX(Double.parseDouble(parts[0]));
                v.setY(Double.parseDouble(parts[1]));
                v.setResistanceFactor(Double.parseDouble(parts[2]));
                v.setName(parts[3]);
                v.setInfectionEfficiency(Double.parseDouble(parts[4]));
                dto.setVirus(v);
            }
            dto.setLevel(Integer.parseInt(reader.readLine().split(": ")[1]));
            dto.setInfectedCells(Integer.parseInt(reader.readLine().split(": ")[1]));
            dto.setResistantCells(Integer.parseInt(reader.readLine().split(": ")[1]));
            int historyCount = Integer.parseInt(reader.readLine().split(": ")[1]);
            List<Boolean> history = new ArrayList<>();
            for (int i = 0; i < historyCount; i++) {
                history.add(Boolean.parseBoolean(reader.readLine()));
            }
            dto.setInfectionHistory(history);
            dto.setMutationRate(Double.parseDouble(reader.readLine().split(": ")[1]));
        }
        return dto;
    }

    public record LoadedSimulation(double mutationRate) {
    }
}
