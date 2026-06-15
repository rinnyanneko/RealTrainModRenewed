# Delete Java files that have Kotlin counterparts
$javaBase = "src\main\java\cc\mirukuneko\realtrainmodrenewed"
$ktBase   = "src\main\kotlin\cc\mirukuneko\realtrainmodrenewed"

$files = @(
    # Phase 1: rail/math
    "rail\math\ILine.java", "rail\math\StraightLine.java", "rail\math\BezierCurve.java",
    # Phase 1: rail/util
    "rail\util\RailDir.java", "rail\util\RailPosition.java", "rail\util\SwitchType.java",
    "rail\util\RailProperties.java", "rail\util\RailMap.java", "rail\util\RailMapBasic.java",
    "rail\util\RailMapSwitch.java", "rail\util\RailMaker.java",
    # Phase 1: MQO objects
    "client\model\mqo\object\MQOVector.java", "client\model\mqo\object\MQOVertex.java",
    "client\model\mqo\object\MQOFace.java", "client\model\mqo\object\MQOObject.java",
    "client\model\mqo\MQOParseResultStatus.java",
    # Phase 2
    "vehicle\VehicleDefinition.java", "installedobject\InstalledObjectDefinition.java",
    "entity\formation\Formation.java", "signal\SignalNetworkSavedData.java",
    "model\MQOModel.java", "model\MQOParser.java", "model\ModelLoader.java",
    "compat\LegacyItemStackBridge.java",
    # Phase 3
    "rail\RailPackLoader.java", "vehicle\VehiclePackLoader.java",
    "installedobject\InstalledObjectPackLoader.java",
    # Phase 4: Items
    "item\IcCardItem.java", "item\MarkerItem.java", "item\CrowbarItem.java",
    "item\SignalCommunicatorItem.java", "item\CarItem.java", "item\RailItem.java",
    "item\InstalledObjectItem.java", "item\WireItem.java", "item\TrainItem.java",
    "item\WrenchItem.java", "item\TrainVehicleItem.java", "item\VehicleFormationItem.java",
    # Phase 5: Blocks
    "block\BallastBlock.java", "block\RailCollisionBlock.java", "block\ScriptBlock.java",
    "block\SignalStateBlock.java", "block\SignalRemoteBlock.java", "block\TrainDetectorBlock.java",
    "block\CrossingGateBlock.java", "block\InstalledObjectBlock.java",
    "block\LargeRailCoreBlock.java", "block\MarkerBlock.java",
    # Phase 6: BlockEntities
    "blockentity\BallastBlockEntity.java", "blockentity\RailCollisionBlockEntity.java",
    "blockentity\SignalRemoteBlockEntity.java", "blockentity\ScriptBlockEntity.java",
    "blockentity\SignalStateBlockEntity.java", "blockentity\TrainDetectorBlockEntity.java",
    "blockentity\MarkerBlockEntity.java", "blockentity\InstalledObjectBlockEntity.java",
    "blockentity\LargeRailCoreBlockEntity.java",
    # Phase 7: Entities
    "entity\TrainSeatEntity.java", "entity\TrainBogieEntity.java",
    "entity\BogieTracker.java", "entity\CarEntity.java",
    # Phase 10: Root registration
    "RealTrainModRenewedBlocks.java", "RealTrainModRenewedItems.java",
    "RealTrainModRenewedEntities.java", "RealTrainModRenewedBlockEntities.java",
    # Phase 8: Renderer partial
    "client\renderer\RealTrainModRenewedRenderers.java",
    "client\renderer\LegacyBlockEntityRenderState.java",
    "client\renderer\LegacyEntityRenderState.java",
    "client\renderer\CarRenderer.java"
)

$deleted = 0
foreach ($f in $files) {
    $jp = Join-Path $javaBase $f
    if (Test-Path $jp) {
        Remove-Item $jp -Force
        Write-Host "DEL: $f"
        $deleted++
    } else {
        Write-Host "MISS: $f"
    }
}
Write-Host "=== Deleted $deleted files ==="
