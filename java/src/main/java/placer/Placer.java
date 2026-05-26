package placer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.util.stream.Collectors;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;

import com.xilinx.rapidwright.edif.EDIFHierPortInst;
import com.xilinx.rapidwright.edif.EDIFHierCellInst;
import com.xilinx.rapidwright.edif.EDIFHierNet;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.DesignTools;
import com.xilinx.rapidwright.design.SiteInst;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.design.SitePinInst;

import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.device.Tile;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.device.SiteTypeEnum;
import com.xilinx.rapidwright.device.ClockRegion;

public abstract class Placer {
    private String placerName;
    protected FileWriter writer;
    protected String rootDir;
    protected String graphicsDir;
    protected String placedDcp;
    protected String printoutDir;

    protected Device device;
    protected Design design;

    protected List<Double> costHistory;
    protected List<Long> moveTimes;
    protected List<Long> evalTimes;
    protected List<Long> renderTimes;
    protected List<Long> writeTimes;
    protected int movesLimit;
    protected List<Double> coolingSchedule;
    protected double currentTemp;

    protected ClockRegion regionConstraint;
    protected Set<SiteTypeEnum> uniqueSiteTypes;
    protected Map<SiteTypeEnum, List<Site>> allSites;
    protected Site[][] rpmGrid;
    protected Map<SiteTypeEnum, Map<Site, SiteInst>> occupiedSites;
    // store chain occupation info for fast site-to-chain access
    protected Map<SiteTypeEnum, Map<Site, List<SiteInst>>> occupiedSiteChains;

    protected Random rand;

    protected String[] FF5_BELS = new String[] { "A5FF", "B5FF", "C5FF", "D5FF" };
    protected String[] FF_BELS = new String[] { "AFF", "BFF", "CFF", "DFF" };
    protected String[] LUT5_BELS = new String[] { "A5LUT", "B5LUT", "C5LUT", "D5LUT" };
    protected String[] LUT6_BELS = new String[] { "A6LUT", "B6LUT", "C6LUT", "D6LUT" };

    public Placer(String rootDir, Design design, Device device) throws IOException {
        this.rootDir = rootDir;
        this.design = design;
        this.device = device;
        this.coolingSchedule = new ArrayList<Double>();
        this.uniqueSiteTypes = new HashSet<>();
        this.occupiedSites = new HashMap<>();
        this.occupiedSiteChains = new HashMap<>();
        this.allSites = new HashMap<>();
        this.costHistory = new ArrayList<>();
        this.moveTimes = new ArrayList<>();
        this.evalTimes = new ArrayList<>();
        this.renderTimes = new ArrayList<>();
        this.writeTimes = new ArrayList<>();
        this.rand = new Random();
    }

    public void makeOutputDirs(String placerName) throws IOException {
        this.placerName = placerName;
        String resultsDir = rootDir + "/outputs/placers/" + placerName;
        this.placedDcp = resultsDir + "/checkpoints";
        File placedDcp = new File(this.placedDcp);
        if (!placedDcp.exists()) {
            placedDcp.mkdirs();
        }
        this.graphicsDir = resultsDir + "/graphics";
        File graphicsDir = new File(this.graphicsDir + "/images");
        if (!graphicsDir.exists()) {
            graphicsDir.mkdirs();
        }
        this.printoutDir = resultsDir + "/printout";
        File printoutDir = new File(this.printoutDir);
        if (!printoutDir.exists()) {
            printoutDir.mkdirs();
        }
        this.writer = new FileWriter(this.printoutDir + "/" + placerName + ".txt");
    }

    public abstract String getPlacerName();

    public abstract void placeDesign(PackedDesign packedDesign) throws IOException;

    public void run(PackedDesign packedDesign) throws IOException {
        placeDesign(packedDesign);
        writer.close();
        design.writeCheckpoint(placedDcp + "/" + placerName + ".dcp");
        DesignTools.toCSV(printoutDir + "/" + placerName, design);
    }

    protected void randomInitialPlacement(PackedDesign packedDesign) throws IOException {
        randomInitSiteChains(packedDesign.DSPSiteInstCascades);
        randomInitSiteChains(packedDesign.CARRYSiteInstChains);
        randomInitSingleSite(packedDesign.RAMSiteInsts);
        randomInitSingleSite(packedDesign.CLBSiteInsts);
    }

    protected void randomInitSingleSite(List<SiteInst> siteInsts) throws IOException {
        for (SiteInst si : siteInsts) {
            Site selectedSite = proposeSite(si, null, false);
            placeSiteInst(si, selectedSite);
        }
    }

    protected void randomInitSiteChains(List<List<SiteInst>> chains) throws IOException {
        for (List<SiteInst> chain : chains) {
            SiteTypeEnum siteType = chain.get(0).getSiteTypeEnum();
            Site selectedAnchor = proposeAnchorSite(chain, null, false);
            for (int i = 0; i < chain.size(); i++) {
                Site newSite = device.getSite(getSiteTypePrefix(siteType) + "X" + selectedAnchor.getInstanceX() +
                        "Y" + (selectedAnchor.getInstanceY() + i));
                occupiedSiteChains.get(siteType).put(newSite, chain);
                placeSiteInst(chain.get(i), newSite);
            }
        }
    } // end randomInitSiteChains()

    protected Site proposeSite(SiteInst si, List<Site> connections, boolean swapEnable) {
        return proposeRandomSite(si, connections, swapEnable);
    }

    protected Site proposeAnchorSite(List<SiteInst> chain, List<Site> connections, boolean swapEnable) {
        return proposeRandomAnchorSite(chain, connections, swapEnable);
    }

    protected Site proposeRandomSite(SiteInst si, List<Site> connections, boolean swapEnable) {
        SiteTypeEnum ste = null;
        if (si.getSiteTypeEnum() == SiteTypeEnum.RAMB18E1) {
            SiteTypeEnum[] compatibleStes = { SiteTypeEnum.RAMB18E1, SiteTypeEnum.FIFO18E1 };
            int randIndex = rand.nextInt(compatibleStes.length);
            ste = compatibleStes[randIndex];
            // System.out.println(ste);
        } else {
            ste = si.getSiteTypeEnum();
        }
        // SiteTypeEnum[] altStes = si.getAlternateSiteTypeEnums();
        // if (altStes.length == 0) {
        // ste = si.getSiteTypeEnum();
        // } else {
        // int randIndex = rand.nextInt(altStes.length);
        // ste = altStes[randIndex];
        // System.out.println(ste);
        // }
        Site selectedSite = null;
        int attempts = 0;
        while (true) {
            if (attempts > 1000)
                throw new IllegalStateException(
                    "ERROR: Could not propose " + ste + " site after 1000 attempts! " +
                    "The design may be too large for this device!"
                );
            int randIndex = rand.nextInt(allSites.get(ste).size());
            selectedSite = allSites.get(ste).get(randIndex);
            if (occupiedSiteChains.containsKey(ste)) { // never propose site swap with a chain
                if (occupiedSiteChains.get(ste).containsKey(selectedSite)) {
                    attempts++;
                    continue;
                }
            } else if (!swapEnable) { // swapping with other single sites only
                if (occupiedSites.get(ste).containsKey(selectedSite)) {
                    attempts++;
                    continue;
                }
            }
            break;
        }
        return selectedSite;
    } // end proposeSite()

    protected Site proposeRandomAnchorSite(List<SiteInst> chain, List<Site> connections, boolean swapEnable) {
        int chainSize = chain.size();
        SiteTypeEnum ste = chain.get(0).getSiteTypeEnum();
        boolean validAnchor = false;
        Site selectedAnchor = null;
        int attempts = 0;
        while (true) {
            int randIndex = rand.nextInt(allSites.get(ste).size());
            selectedAnchor = allSites.get(ste).get(randIndex);
            int x = selectedAnchor.getInstanceX();
            int y = selectedAnchor.getInstanceY();
            for (int i = 0; i < chainSize; i++) {
                Site site = device.getSite(getSiteTypePrefix(ste) + "X" + x + "Y" + (y + i));
                if (site == null) {
                    validAnchor = false;
                    break;
                }
                if (!swapEnable) {
                    if (occupiedSites.get(ste).containsKey(site)) {
                        validAnchor = false;
                        break;
                    }
                }
                validAnchor = true;
            }
            attempts++;
            if (attempts > 1000)
                throw new IllegalStateException(
                        "ERROR: Could not propose " + ste + " chain anchor after 1000 attempts!");
            if (validAnchor)
                break;
        }
        return selectedAnchor;
    }

    protected void initRpmGrid() throws IOException {
        int x_high = 0;
        int y_high = 0;
        int x_low = Integer.MAX_VALUE;
        int y_low = Integer.MAX_VALUE;
        for (Map.Entry<SiteTypeEnum, List<Site>> entry : this.allSites.entrySet()) {
            for (Site site : entry.getValue()) {
                int site_x = site.getRpmX();
                if (site_x > x_high)
                    x_high = site_x;
                if (site_x < x_low)
                    x_low = site_x;
                int site_y = site.getRpmY();
                if (site_y > y_high)
                    y_high = site_y;
                if (site_y < y_low)
                    y_low = site_y;
            }
        }
        int width = x_high - x_low + 1;
        int height = y_high - y_low + 1;
        this.rpmGrid = new Site[width][height];

        for (Map.Entry<SiteTypeEnum, List<Site>> entry : this.allSites.entrySet()) {
            for (Site site : entry.getValue()) {
                int x = site.getRpmX();
                int y = site.getRpmY();
                this.rpmGrid[x][y] = site;
            }
        }
    }

    protected void initSites() throws IOException {
        for (Site site : device.getAllSites()) {
            SiteTypeEnum siteType = site.getSiteTypeEnum();
            if (uniqueSiteTypes.add(siteType)) {
                List<Site> compatibleSites = new ArrayList<>(Arrays.asList(device.getAllSitesOfType(siteType)));
                if (regionConstraint != null) {
                    compatibleSites = compatibleSites.stream()
                            .filter(s -> s.getClockRegion() == null
                                    ? s.isGlobalClkBuffer()
                                    : s.getClockRegion().equals(regionConstraint))
                            .collect(Collectors.toList());
                }
                allSites.put(siteType, compatibleSites);
                occupiedSites.put(siteType, new HashMap<>());
            }
        }
        occupiedSiteChains.put(SiteTypeEnum.DSP48E1, new HashMap<>());
        occupiedSiteChains.put(SiteTypeEnum.SLICEL, new HashMap<>());
        occupiedSiteChains.put(SiteTypeEnum.SLICEM, new HashMap<>());
    }

    protected void placeSiteInst(SiteInst si, Site site) {
        occupiedSites.get(si.getSiteTypeEnum()).put(site, si);
        si.place(site);
    }

    protected void unplaceSiteInst(SiteInst si) {
        occupiedSites.get(si.getSiteTypeEnum()).remove(si.getSite());
        si.unPlace();
    }

    protected List<Site> findConnectedSites(SiteInst si, List<Site> selfConns) {
        List<Site> connectedSites = si.getSitePinInsts().stream()
                .map(spi -> spi.getNet())
                .filter(net -> net != null)
                .filter(net -> !net.isClockNet() && !net.isStaticNet())
                .map(net -> net.getPins())
                .map(spis -> spis.stream()
                        .map(spi -> spi.getSite())
                        // for chain swaps, ignore connections within the buffers.
                        // DSP cascades can have very many self connections.
                        // Allows DSP cascades to move more freely
                        .filter(site -> !((selfConns != null) && selfConns.contains(site)))
                        .collect(Collectors.toList()))
                .flatMap(List::stream) // List<List<Site>> into List<Site>
                .collect(Collectors.toList());
        return connectedSites;
    }

    protected boolean evaluateMoveAcceptance(double oldCost, double newCost) {
        // if the new cost is lower, accept it outright
        if (newCost < oldCost) {
            return true;
        }
        // otherwise, evaluate probability to accept higher cost
        double delta = newCost - oldCost;
        double acceptanceProbability = Math.exp(-delta / this.currentTemp);
        return Math.random() < acceptanceProbability;
    }

    protected double evaluateSite(List<Site> sinkSites, Site srcSite) throws IOException {
        double cost = 0;
        for (Site sinkSite : sinkSites) {
            if (sinkSite.isGlobalClkBuffer()) {
                // System.out.println("Skipped global clock buffer evaluation.");
                continue;
            }
            // if (sinkSite.isGlobalClkPad()) {
            // // System.out.println("Skipped global clock pad evaluation.");
            // continue;
            // }
            cost = cost + srcSite.getTile().getTileManhattanDistance(sinkSite.getTile());
        }
        return cost;
    }

    public double evaluateDesign() throws IOException {
        double cost = 0;
        Collection<Net> nets = design.getNets();
        for (Net net : nets) {
            // if (net.isClockNet() || net.isStaticNet()) {
            // // System.out.println("Skipped static net evaluation.");
            // continue;
            // }
            // if (net.isStaticNet()) {
            // // System.out.println("Skipped clock net evaluation.");
            // continue;
            // }
            Tile srcTile = net.getSourceTile();
            if (srcTile == null) // tile is null if its' purely intrasite!
                continue;
            List<Tile> sinkTiles = net.getSinkPins().stream()
                    .map(spi -> spi.getTile())
                    .collect(Collectors.toList());
            for (Tile sinkTile : sinkTiles) {
                cost = cost + srcTile.getTileManhattanDistance(sinkTile);
            }
        }
        return cost;
    }

    protected void unplaceAllSiteInsts(PackedDesign packedDesign) throws IOException {
        for (List<SiteInst> cascade : packedDesign.DSPSiteInstCascades) {
            for (SiteInst si : cascade) {
                unplaceSiteInst(si);
            }
        }
        for (List<SiteInst> chain : packedDesign.CARRYSiteInstChains) {
            for (SiteInst si : chain) {
                unplaceSiteInst(si);
            }
        }
        for (SiteInst si : packedDesign.CLBSiteInsts) {
            unplaceSiteInst(si);
        }
        for (SiteInst si : packedDesign.RAMSiteInsts) {
            unplaceSiteInst(si);
        }
    }

    protected String getSiteTypePrefix(SiteTypeEnum siteType) {
        String siteTypePrefix = null;
        if (siteType == SiteTypeEnum.DSP48E1)
            siteTypePrefix = "DSP48_";
        else if (siteType == SiteTypeEnum.SLICEL || siteType == SiteTypeEnum.SLICEM)
            siteTypePrefix = "SLICE_";
        else if (siteType == SiteTypeEnum.RAMB18E1)
            siteTypePrefix = "RAMB18_";
        else
            throw new IllegalStateException("ERROR: Could not assign a String prefix to  SiteTypeEnum: " + siteType);
        return siteTypePrefix;
    }

    public void printTimingBenchmarks() throws IOException {
        writer.write("\n\nPrinting Move Times... ");
        for (int i = 0; i < moveTimes.size(); i++) {
            writer.write("\n\tIter: " + i + ", Time (ms): " + moveTimes.get(i));
        }
        writer.write("\n\nPrinting Eval Times... ");
        for (int i = 0; i < evalTimes.size(); i++) {
            writer.write("\n\tIter: " + i + ", Time (ms): " + evalTimes.get(i));
        }
        writer.write("\n\nPrinting Render Times...");
        for (int i = 0; i < renderTimes.size(); i++) {
            writer.write("\n\tIter: " + i + ", Time (ms): " + renderTimes.get(i));
        }
        writer.write("\n\nPrinting DCP Write Times... ");
        for (int i = 0; i < writeTimes.size(); i++) {
            writer.write("\n\tIter: " + i + ", Time (ms): " + writeTimes.get(i));
        }
    }

    public void exportCostHistory(String fileName) throws IOException {
        FileWriter csv = new FileWriter(fileName);
        csv.write("Iter, Cost");
        for (int i = 0; i < this.costHistory.size(); i++) {
            csv.write("\n" + i + ", " + this.costHistory.get(i));
        }
        if (csv != null)
            csv.close();
    }

    public List<Double> getCostHistory() {
        return this.costHistory;
    }

    public List<Double> getCoolingSchedule() {
        return this.getCoolingSchedule();
    }

    public void printSiteInstPlacements(PackedDesign packedDesign) throws IOException {
        for (List<SiteInst> cascade : packedDesign.DSPSiteInstCascades) {
            for (SiteInst si : cascade) {
                String site = si == null ? "Null!" : si.getName();
                writer.write("\nSiteInst: " + si.getName() + ", Site: " + site);
            }
        }
        for (List<SiteInst> chain : packedDesign.CARRYSiteInstChains) {
            for (SiteInst si : chain) {
                String site = si == null ? "Null!" : si.getName();
                writer.write("\nSiteInst: " + si.getName() + ", Site: " + site);
            }
        }
        for (SiteInst si : packedDesign.CLBSiteInsts) {
            String site = si == null ? "Null!" : si.getName();
            writer.write("\nSiteInst: " + si.getName() + ", Site: " + site);
        }
        for (SiteInst si : packedDesign.RAMSiteInsts) {
            String site = si == null ? "Null!" : si.getName();
            writer.write("\nSiteInst: " + si.getName() + ", Site: " + site);
        }
    }

    public void addIOBuffersToNetlist() throws IOException {
        writer.write("\n\nAdding IOBuffers to Netlist...");
        for (Net net : design.getNets()) {
            for (SiteInst si : net.getSiteInsts()) {
                if (si.getSiteTypeEnum() == SiteTypeEnum.IOB33) {
                    DesignTools.createMissingSitePinInsts(design, net);
                    // for (SitePinInst spi : si.getSitePinInsts()) {
                    // net.addPin(spi);
                    // writer.write("\n\tAdded Pin: " + spi + " to net: " + net);
                    // }
                }
            }
        }
    }

    public void printNetlist() throws IOException {
        writer.write("\n\nPrinting Netlist with SitePinInsts... ");
        for (Net net : design.getNets()) {
            writer.write("\n\tNet: " + net.getName());
            for (SitePinInst spi : net.getPins()) {
                writer.write("\n\t\tSPI: " + spi);
            }
        }

        // writer.write("\n\nPrinting Netlist with SiteInsts...");
        // for (Net net : design.getNets()) {
        // writer.write("\n\tNet: " + net.getName());
        // for (SiteInst si : net.getSiteInsts()) {
        // writer.write("\n\t\tSiteInst: " + si);
        // for (SitePinInst spi : si.getSitePinInsts()) {
        // writer.write("\n\t\t\tSPI: " + spi);
        // }
        // }
        // }

        // writer.write("\n\nPrinting Netlist with EDIFHierCellInsts...");
        // for (Net net : design.getNets()) {
        // writer.write("\n\tNet: " + net.getName());
        // EDIFHierNet edifNet = net.getLogicalHierNet();
        // if (edifNet == null)
        // continue;

        // for (EDIFHierPortInst ehpi : net.getLogicalHierNet().getLeafHierPortInsts())
        // {
        // writer.write("\n\t\tEHPI: " + ehpi);
        // EDIFHierCellInst ehci = ehpi.getHierarchicalInst();
        // }
        // }

    }

} // end class Placer
