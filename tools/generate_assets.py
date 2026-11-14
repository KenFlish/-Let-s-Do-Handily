"""Generate scenes and translations. Book node placement credits: LICENSE_HANDSON_DELIGHT."""
import json
from copy import deepcopy
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/handson_farm_charm'

def approach(scene):
    import math
    camera = scene['camera']
    x,y,z = camera['position']
    camera['glances'] = []
    for turn in range(4):
        a = math.radians(-turn*90)
        camera['glances'].append({'id':f'approach_{turn}',
            'position':[8+(x-8)*math.cos(a)+(z-8)*math.sin(a), y, 8-(x-8)*math.sin(a)+(z-8)*math.cos(a)],
            'target':camera['target'], 'time':.25, 'back_on_take':False, 'back_zone':0, 'hint':False})
    ui=[]
    for node in scene['nodes']:
        if node['id'] in ('book_open','result','dump') or node['id'].startswith('result_'):
            node['pos'][0]-=8;node['pos'][2]-=8;ui.append(node)
    scene['nodes']=[n for n in scene['nodes'] if n not in ui]+[{'id':'approach_ui','pos':[8,0,8],'pickable':False,'children':ui}]
    return scene

def find_node(scene, node_id):
    for node in scene['nodes']:
        if node.get('id') == node_id:
            return node
        for child in node.get('children', ()):
            if child.get('id') == node_id:
                return child
    raise KeyError(node_id)

def write(path, data):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

for bowl in [False, True]:
    nodes = []
    if not bowl:
        nodes += [
            {'id': 'pot_body', 'pickable': False},
            {'id': 'broth', 'pickable': False, 'tank': {'tank': 'broth', 'inner': {'from': [4.05, 2.05, 4.05], 'to': [11.95, 8.95, 11.95]}}},
            {'id': 'spoon', 'pos': [8, 0, 8], 'pickable': False, 'children': [
                {'id': 'stir_handle', 'strokes': True, 'collider': {'from': [-8.5, 7.5, -1], 'to': [-2.5, 13, 3]},
                 'tooltip': 'handson_farm_charm.stir'}
            ]},
        ]
    # A handle hit zone separate from the visible native bowl whisk / our spoon.
    if bowl: nodes.append({'id': 'stir_handle', 'strokes': True,
                  'collider': {'from': [6.2, 12, 6.2], 'to': [9.8, 18, 9.8]},
                  'tooltip': 'handson_farm_charm.stir'})
    nodes.append({'id': 'mouth', 'drop_zone': 'ingredients',
                  'collider': {'from': [4, 6, 4], 'to': [12, 7.5, 12]}})
    count = 4 if bowl else 6
    positions = [[5.3, 6.5, 5.3], [10.7, 6.5, 5.3], [5.3, 6.5, 10.7], [10.7, 6.5, 10.7]] if bowl else [
        [5.2, 8.2, 5.2], [8, 8.2, 4.7], [10.8, 8.2, 5.2], [5.2, 8.2, 10.8], [8, 8.2, 11.3], [10.8, 8.2, 10.8]]
    for i in range(count):
        nodes.append({'id': f'ingredient_{i}', 'pos': positions[i],
                      'collider': {'from': [-1.2, 0, -1.2], 'to': [1.2, .7, 1.2]},
                      'slot': {'slot': f'ingredients[{i}]', 'size': 2.4, 'item_size': 2.2,
                               'display': 'hidden' if bowl else 'flat', 'counts': 'hover', 'highlight': False}})
    result = {'id': 'result', 'pos': [-1, 16, -1], 'rotation': [-50, 0, 0], 'tooltip': 'handson_farm_charm.result'}
    if bowl: result['slot'] = {'slot': 'result[0]', 'size': 4, 'display': 'hidden', 'highlight': False}
    else: result['collider'] = {'from': [-2.3, -.5, -2.3], 'to': [2.3, .7, 2.3]}
    nodes.append(result)
    # Book placement/collider adapted from Hands-On Delight (MIT, LICENSE_HANDSON_DELIGHT).
    nodes.append({'id': 'book_open', 'pos': [16.33, 21.22, -4], 'rotation': [-50, 0, 0], 'scale': .69,
                  'hidden': True, 'collider': {'from': [-4.4, -.4, -3.9], 'to': [4.4, 1.4, 3.9]}})
    scene = {
        'work_plane': 14, 'idle_visible': False,
        'camera': {'position': [8, 41, -21], 'target': [8, 6.5, 8], 'fov': 30,
                   'neighbors': {'enabled': False}},
        'interface': {'hide_player': True, 'inventory_panel': False, 'outline_color': '#FFFFE8C0', 'shadows': False,
                      'carry_lift': 4, 'carry_stick': False},
        'inventory_drawer': {'side': 'bottom', 'style': 'minimal', 'always_open': True, 'compact': True,
                             'background': '#00000000', 'slot_color': '#8C1E140C'},
        'nodes': nodes,
    }
    write('diegesis/scenes/' + ('crafting_bowl' if bowl else 'cooking_pot') + '.json', approach(deepcopy(scene)))

# Each appliance keeps its own native input, fuel and container slots.
from copy import deepcopy
for kind, count in [('mincer', 1), ('stove', 3), ('roaster', 6)]:
    appliance = deepcopy(scene)
    appliance['work_plane'] = 18 if kind == 'stove' else 14
    nodes = []
    if kind == 'mincer':
        nodes.append({'id': 'crank_handle', 'pos': [1.7, 10, 7.8], 'rotation': [0, 0, 90],
                      'strokes': True, 'collider': {'from': [-5, -2, -6], 'to': [5, 2, 6]},
                      'tooltip': 'handson_farm_charm.crank'})
        positions = [[6.5, 13.3, 8]]
    elif kind == 'stove':
        # A front view of the baking chamber; no controls or camera above the oven.
        appliance['camera'] = {'position': [8, 11, -31], 'target': [8, 8, 7], 'fov': 37, 'neighbors': {'enabled': False}}
        positions = [[4.5, 10.5, -.6], [8, 10.5, -.6], [11.5, 10.5, -.6]]
        nodes.append({'id': 'fuel', 'pos': [8, 3, -1], 'rotation': [-90,0,0], 'slot': {'slot': 'fuel[0]', 'size': 3.5},
                      'tooltip': 'handson_farm_charm.fuel'})
        nodes.append({'id': 'fire', 'pos': [15, 3, -1], 'scale': .18,
                      'visual': {'item': 'minecraft:flint_and_steel', 'anchor': [8, 8, 8]},
                      'collider': {'from': [-8, -8, -8], 'to': [8, 8, 8]}, 'tooltip': 'handson_farm_charm.fire'})
    else:
        nodes += [{'id': 'roaster_body', 'pickable': False},
                  {'id': 'lid', 'pos': [8, 6, 12],
                   'collider': {'from': [-5.2, -.2, -8.2], 'to': [5.2, 2.3, .3]},
                   'tooltip': 'handson_farm_charm.lid'}]
        positions = [[5.2,1.3,6.2],[9.8,1.4,8.4],[6.9,1.7,10.1],[10.1,2,5.8],[4.9,2.2,9.9],[8,2.45,7.6]]
    for i, position in enumerate(positions):
        nodes.append({'id': f'ingredient_{i}', 'pos': position,
                      'collider': {'from': [-1.5, 0, -1.5], 'to': [1.5, 1, 1.5]},
                      'slot': {'slot': f'ingredients[{i}]', 'size': 3, 'item_size': 2.6,
                               'display': 'hidden' if kind in ('mincer','roaster') else 'flat', 'counts': 'hover', 'highlight': False}})
        if kind == 'stove': nodes[-1]['rotation'] = [-90,0,0]
        if kind == 'roaster': nodes[-1]['rotation'] = [0,[-24,19,66,-48,32,-12][i],0]
    # Only ingredients accept a group drop. Tools, the book, fuel, and output have separate hit zones.
    if kind != 'stove':
        nodes.append({'id': 'mouth', 'drop_zone': 'ingredients',
                      'collider': {'from': [3, 2, 4] if kind == 'roaster' else [3, 12, 4],
                                   'to': [13, 4, 12] if kind == 'roaster' else [10, 13, 12]}})
    nodes += [deepcopy(n) for n in scene['nodes'] if n['id'] in ('result', 'book_open')]
    for n in nodes:
        if n['id'] == 'result' and kind == 'roaster':
            n.pop('slot',None); n['collider'] = {'from': [-2.3,-.5,-2.3], 'to': [2.3,.7,2.3]}
    if kind == 'stove':
        for n in nodes:
            if n['id'] == 'result': n['pos'] = [-5, 11, -3]; n['rotation'] = [-85,0,0]
            if n['id'] == 'book_open': n['pos'] = [21, 11, -4]; n['rotation'] = [-85,0,0]
    appliance['nodes'] = nodes
    write(f'diegesis/scenes/{kind}.json', approach(appliance) if kind in ('roaster', 'mincer') else appliance)

# The native board renderer supplies the knife, chopping animation and assembly layers.
board = deepcopy(scene)
board['camera'] = {'position': [8, 35.5, -21], 'target': [8, 1, 8], 'fov': 30, 'neighbors': {'enabled': False}}
board['work_plane'] = 4
board['nodes'] = [deepcopy(find_node(scene, 'book_open'))]
board['nodes'][0]['pos'] = [15.5, 15.72, -4]
board['nodes'] += [
    {'id': 'surface', 'collider': {'from': [1, 0, 3], 'to': [11, 5, 13]}, 'tooltip': 'handson_farm_charm.board.surface'},
    {'id': 'carved', 'pos': [6, 3.5, 8], 'rotation': [0, 25, -147], 'scale': .5, 'pickable': False},
    {'id': 'food', 'pos': [6, 1.05, 8], 'pickable': False},
    {'id': 'marks', 'pos': [6, 1.05, 8], 'pickable': False},
    {'id': 'knife_turn', 'pos': [6, 1, 8], 'pickable': False, 'children': [
        {'id': 'knife_model', 'rotation': [20, 0, -135], 'scale': .45, 'pickable': False}]},
]
for i in range(9):
    board['nodes'].append({'id': f'layer_{i}', 'pos': [6, 1.12 + i * .65, 8], 'pickable': False})
for i in range(8):
    board['nodes'].append({'id': f'result_{i}', 'pos': [-5 - i % 2 * 4, 8 - i // 2 * 3, -2], 'rotation': [-50, 0, 0],
                           'slot': {'slot': f'result[{i}]', 'size': 3.5, 'display': 'flat', 'counts': 'always', 'highlight': False},
                           'tooltip': 'handson_farm_charm.result'})
write('diegesis/scenes/cutting_board.json', approach(board))

pot = json.loads((ROOT / 'diegesis/scenes/cooking_pot.json').read_text(encoding='utf-8'))
for n in pot['nodes']:
    if n['id'] == 'broth': n['tank']['inner']['to'][1] = 10.95
    if n['id'].startswith('ingredient_'): n['pos'][1] += 2.5
    if n['id'] == 'mouth': n['collider'] = {'from': [4, 11, 4], 'to': [12, 12, 12]}
    if n['id'] == 'spoon':
        n['children'][0]['collider'] = {'from': [-8.5, 10.5, -1], 'to': [-2.5, 16, 3]}
write('diegesis/scenes/candlelight_pot.json', pot)
pan = json.loads((ROOT / 'diegesis/scenes/roaster.json').read_text(encoding='utf-8'))
pan['nodes'] = [n for n in pan['nodes'] if n['id'] not in ('roaster_body', 'lid')]
for n in pan['nodes']:
    if n['id'].startswith('ingredient_'): n['pos'][1] += 1.1
    if n['id'] == 'mouth': n['collider'] = {'from': [3, 2.2, 3], 'to': [13, 3, 13]}
write('diegesis/scenes/candlelight_pan.json', pan)
jam=deepcopy(scene)
jam['nodes']=[deepcopy(find_node(scene,'book_open'))]+[
    {'id':'mouth','collider':{'from':[4.5,3,4.5],'to':[11.5,7,11.5]}},
    {'id':'stir_handle','strokes':True,'collider':{'from':[5.5,8,5.5],'to':[10.5,16,10.5]},'tooltip':'handson_farm_charm.jam.stir'},
    {'id':'result','pos':[-1,16,-1],'rotation':[-50,0,0],'collider':{'from':[-2.3,-.5,-2.3],'to':[2.3,.7,2.3]}},
    {'id':'dump','pos':[-3,3,8],'scale':.14,'visual':{'item':'minecraft:bucket','anchor':[8,8,8]},
        'collider':{'from':[-8,-2,-8],'to':[8,4,8]},'tooltip':'handson_farm_charm.jam.dump'}]
jam['captions']={'edge':'top','background':'#70000000'}
write('diegesis/scenes/jam_pot.json',approach(jam))
en = {
    'pot_lid': 'Open / close the lid',
    'board.book_all': 'All recipes', 'board.book_tools': 'Axe work',
    'jam.stir': 'Stir with circular mouse movements',
    'jam.stir_due': 'Stir the jam!', 'jam.cooking': 'Jam: %s%%',
    'jam.burnt': 'Burnt jam — empty the pot', 'jam.bottle': 'Fill a jam jar',
    'jam.dump': 'Empty pot — a cooking batch will be discarded',
    'board.cut': 'Use a knife or cleaver to cut along the dashed marks',
    'board.surface': 'Left: cut / assemble. Right: take',
    'board.book_cut': 'Knife / cleaver',
    'board.book_assembly': 'Assemble',
    'board.assemble': 'Assemble the dish (no knife needed)',
    'board.knife': 'Place a knife here to chop; remove it to assemble dishes',
    'lid': 'Click the lid to open or close. Cooking requires a closed lid',
    'close_lid': 'Close the lid to cook',
    'badge.batch': '%s ready · next: %s%%',
    'crank': 'Take the crank and turn the mouse in circles',
    'mincing': 'Grinding: %s%%', 'fuel': 'Fuel',
    'fire': 'Left-click: light the stove. Right-click: extinguish',
    'book.seconds': '%s s',
    'badge.ready': 'Ready · %s servings',
    'help': 'Click the handle, stir in circles. Click again to release. Esc: leave.',
    'stir': 'Hold or click the handle and move in circles. Pot: up to 1.5x cooking speed',
    'result': 'Prepared food', 'containers': 'Empty serving containers', 'progress': 'Progress',
    'mixing': 'Mixing: %s%%', 'cooking': 'Cooking: %s%%', 'no_heat': 'No heat source — %s%%',
    'available': 'Click to add the missing ingredients for one recipe', 'unavailable': 'Ingredients missing or station cannot fit this recipe',
    'fill_failed': 'Cannot add this recipe: check ingredients, free space and the lid.',
    'serving_failed': 'A matching serving container and free inventory space are required',
    'serve_with': '%s — serve with: %s',
    'book.pot_time': '23–35 s',
    'next': 'Next page', 'prev': 'Previous page',
}
ru = {
    'pot_lid': 'Открыть / закрыть крышку',
    'board.book_all': 'Все рецепты', 'board.book_tools': 'Работа топором',
    'jam.stir': 'Вращайте венчик мышью',
    'jam.stir_due': 'Пора помешать варенье!', 'jam.cooking': 'Варенье: %s%%',
    'jam.burnt': 'Варенье сгорело — очистите кастрюлю', 'jam.bottle': 'Наполнить банку вареньем',
    'jam.dump': 'Очистить кастрюлю — готовящееся варенье пропадёт',
    'board.cut': 'Положите нож или тесак и режьте по пунктирным отметкам',
    'board.surface': 'ЛКМ: резать / собрать. ПКМ: забрать',
    'board.book_cut': 'Нож / тесак',
    'board.book_assembly': 'Сборка',
    'board.assemble': 'Собрать блюдо (нож не нужен)',
    'board.knife': 'Положите нож для нарезки; уберите его для сборки блюда',
    'lid': 'Нажмите на крышку, чтобы открыть или закрыть. Готовит с закрытой крышкой',
    'close_lid': 'Закройте крышку для готовки',
    'badge.batch': 'Готово: %s · следующая: %s%%',
    'crank': 'Возьмитесь за ручку и вращайте мышь по кругу',
    'mincing': 'Измельчение: %s%%', 'fuel': 'Топливо',
    'fire': 'ЛКМ: разжечь печь. ПКМ: потушить',
    'book.seconds': '%s с',
    'badge.ready': 'Готово · порций: %s',
    'help': 'Нажмите на ручку и двигайте мышь по кругу. Ещё щелчок: отпустить. Esc: выйти.',
    'stir': 'Удерживайте или возьмите ручку щелчком и вращайте мышь. Котёл: скорость до ×1,5',
    'result': 'Готовая еда', 'containers': 'Пустая посуда для порций', 'progress': 'Готовность',
    'mixing': 'Замешивание: %s%%', 'cooking': 'Приготовление: %s%%', 'no_heat': 'Нет источника тепла — %s%%',
    'available': 'Нажмите, чтобы добавить недостающие ингредиенты одного рецепта',
    'unavailable': 'Не хватает ингредиентов или места для этого рецепта',
    'fill_failed': 'Не удалось добавить рецепт: проверьте ингредиенты, свободное место и крышку.',
    'serving_failed': 'Для получения нужны подходящая посуда и свободное место',
    'serve_with': '%s — для подачи: %s',
    'book.pot_time': '23–35 с',
    'next': 'Следующая страница', 'prev': 'Предыдущая страница',
}
for lang, messages in [('en_us', en), ('ru_ru', ru)]:
    write(f'lang/{lang}.json', {'handson_farm_charm.' + k: v for k, v in messages.items()})
