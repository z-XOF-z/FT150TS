"""Une la caja y el porta-batería (pegado a la pared trasera) solo para ver cómo quedan juntos."""
import trimesh

caja = trimesh.load("caja_ensamble.stl")
bat = trimesh.load("bateria_ensamble.stl")
bat.apply_translation((0, 105.2 / 2, 0))
print("cerrados:", caja.is_watertight, bat.is_watertight)
print("caja", caja.bounds.round(1).tolist(), "bateria", bat.bounds.round(1).tolist())
trimesh.util.concatenate([caja, bat]).export("ensamble_con_bateria.stl")
