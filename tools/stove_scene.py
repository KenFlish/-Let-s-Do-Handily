"""Farm & Charm stove, adapted from Hands-On Vanilla (MIT, LICENSE_HANDSON_VANILLA)."""
def stove_scene():
    nodes = [
        {'id':'body','pickable':False,'hidden':True},
    ]
    for chamber,x,y in [('mouth',3,8),('firebox',4,1)]:
        nodes.append({'id':f'{chamber}_door','pos':[x,y,0],'pickable':False,'hidden':True})
    # Three native ingredient slots share the left of the grate; output stands at its right.
    for i,(x,z) in enumerate([(11,2.1),(8.5,3.3),(11,4.7)]):
        nodes.append({'id':f'ingredient_{i}','pos':[x,8.15,z],
                      'slot':{'slot':f'ingredients[{i}]','display':'hidden','size':2.4,'item_size':2.2,'counts':'hover','highlight':False},
                      'collider':{'from':[-1.2,0,-.8],'to':[1.2,3,.8]}})
    nodes += [
        {'id':'mouth','drop_zone':'ingredients','collider':{'from':[7.2,8.1,-.15],'to':[12.6,12,1]}},
        {'id':'result','pos':[5.6,8.15,2.8],'collider':{'from':[-1.7,0,-2.9],'to':[1.7,3.8,2]}},
        {'id':'fuel','pos':[8,1,2.2],
         'slot':{'slot':'fuel[0]','display':'hidden','size':2.8,'counts':'hover','highlight':False},
         'collider':{'from':[-4,0,-2.3],'to':[4,4,2]}},
        {'id':'fire','collider':{'from':[3,1,-.2],'to':[4,5,.1]}},
        {'id':'progress','pos':[8,8.2,.4],'pickable':False,'hidden':True,
         'children':[{'id':'progress_fill','pickable':False,'hidden':True}]},
        {'id':'arrival','pos':[5.6,9,2.8],'pickable':False,'hidden':True},
        {'id':'book_open','pos':[22,11,-2],'rotation':[-85,0,0],'scale':.69,'hidden':True,
         'collider':{'from':[-4.4,-.4,-3.9],'to':[4.4,1.4,3.9]}},
    ]
    return {
        'work_plane':0,'idle_visible':True,'clear_view':{'blocks':False},
        'camera':{'position':[-2.5,14.5,-28],'target':[8,7.5,3],'fov':40,'neighbors':{'enabled':False}},
        'interface':{'hide_player':True,'inventory_panel':False,'shadows':False,'carry_lift':.5,
                     'carry_stick':False,'outline_color':'#FFFFE8C0'},
        'inventory_drawer':{'side':'bottom','style':'minimal','always_open':True,'compact':True,
                            'background':'#00000000','slot_color':'#8C1E140C'},
        'nodes':nodes,
    }
