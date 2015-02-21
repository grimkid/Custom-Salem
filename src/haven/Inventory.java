/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import java.util.*;
import com.google.common.collect.*;

public class Inventory extends Widget implements DTarget {
    private static final Tex obt = Resource.loadtex("gfx/hud/inv/obt");
    private static final Tex obr = Resource.loadtex("gfx/hud/inv/obr");
    private static final Tex obb = Resource.loadtex("gfx/hud/inv/obb");
    private static final Tex obl = Resource.loadtex("gfx/hud/inv/obl");
    private static final Tex ctl = Resource.loadtex("gfx/hud/inv/octl");
    private static final Tex ctr = Resource.loadtex("gfx/hud/inv/octr");
    private static final Tex cbr = Resource.loadtex("gfx/hud/inv/ocbr");
    private static final Tex cbl = Resource.loadtex("gfx/hud/inv/ocbl");
    private static final Tex bsq = Resource.loadtex("gfx/hud/inv/sq");
    public static final Coord sqsz = bsq.sz();
    public static final Coord isqsz = new Coord(40, 40);
    public static final Tex sqlite = Resource.loadtex("gfx/hud/inv/sq1");
    public static final Coord sqlo = new Coord(4, 4);
    public static final Tex refl = Resource.loadtex("gfx/hud/invref");

    private static final Comparator<WItem> cmp_asc = new Comparator<WItem>() {
	@Override
	public int compare(WItem o1, WItem o2) {
	    float c1 = o1.carats.get();
	    float c2 = o2.carats.get();

	    if(c1 == c2) {
		Alchemy a = o1.alch.get();
		double q1 = (a == null) ? 0 : a.purity();

		a = o2.alch.get();
		double q2 = (a == null) ? 0 : a.purity();

		if(q1 == q2) {
		    return 0;
		} else if(q1 > q2) {
		    return 1;
		} else {
		    return -1;
		}
	    } else if(c1 > c2){
		return 1;
	    } else {
		return -1;
	    }
	}
    };
    private static final Comparator<WItem> cmp_desc = new Comparator<WItem>() {
	@Override
	public int compare(WItem o1, WItem o2) {
	    return cmp_asc.compare(o2, o1);
	}
    };

    Coord isz,isz_client;
    Map<GItem, WItem> wmap = new HashMap<GItem, WItem>();
    public int newseq = 0;

    @RName("inv")
    public static class $_ implements Factory {
	public Widget create(Coord c, Widget parent, Object[] args) {
	    return(new Inventory(c, (Coord)args[0], parent));
	}
    }

    public void draw(GOut g) {
	invsq(g, Coord.z, isz_client);
	for(Coord cc = new Coord(0, 0); cc.y < isz_client.y; cc.y++) {
	    for(cc.x = 0; cc.x < isz_client.x; cc.x++) {
		invrefl(g, sqoff(cc), isqsz);
	    }
	}
	super.draw(g);
    }

    BiMap<Coord,Coord> dictionaryClientServer;
    boolean isTranslated = false;
    public Inventory(Coord c, Coord sz, Widget parent) {
	super(c, invsz(sz), parent);
	isz = sz;
        isz_client = sz;
        dictionaryClientServer = HashBiMap.create();
    }

    public void sortItemsLocally()
    {
        isTranslated = true;
        //first step: deciding the size of the sorted inventory
        int nr_items = wmap.size();
        float aspect_ratio = 6/4;
        int width  = Math.max(4,1+(int) Math.ceil(Math.sqrt(aspect_ratio*nr_items)));
        int height = Math.max(4,1+(int) Math.ceil(nr_items/width));
        //now sort the item array
        List<WItem> array = new ArrayList<WItem>(wmap.values());
        Collections.sort(array, new Comparator<WItem>(){
            @Override
            public int compare(WItem o1, WItem o2) {
                return o1.item.resname().compareTo(o2.item.resname());
            }
        });
        //assign the new locations to each of the items and add new translations
        int index = 0;
        BiMap<Coord,Coord> newdictionary = HashBiMap.create();
        for(WItem w : array)
        {
            Coord newloc = new Coord((index%(width)),(int)(index/(width)));
            
            //adding the translation to the dictionary
                Coord currentloc = sqroff(w.c);
                //was oldloc already translated?
                Coord translatedoldloc = dictionaryClientServer.get(currentloc);
                if(translatedoldloc != null)
                    currentloc = translatedoldloc;
                
                newdictionary.put(newloc,currentloc);
                
            //moving the widget to its ordered place
            w.c = sqoff(newloc);
            
            //on to the next location
            index++;
        }
        dictionaryClientServer = newdictionary;
        
        //resize the inventory to the new set-up
        this.updateClientSideSize();
    }
    
    public Coord translateCoordinatesClientServer(Coord client)
    {
        if(!isTranslated)
            return client;
        Coord server = client;
        if(dictionaryClientServer.containsKey(client))
        {
            server = dictionaryClientServer.get(client);
        }
        return server;
    }
    
    public Coord translateCoordinatesServerClient(Coord server)
    {
        if(!isTranslated)
            return server;
        Coord client = server;
        BiMap<Coord,Coord> dictionaryServerClient = dictionaryClientServer.inverse();
        if(dictionaryServerClient.containsKey(server))
        {
            client = dictionaryServerClient.get(server);
        }
        else if(dictionaryClientServer.containsKey(client)){
            //we already mapped something to this spot ourself!
            //put it somewhere else
            int width = isz_client.x;
            int height = isz_client.y;
            int index = 0;
            Coord newloc;
            do{
                newloc = new Coord((index%(width-1)),(int)(index/(width-1)));
                index++;
            }while(dictionaryClientServer.containsKey(newloc));
            client = newloc;
            dictionaryClientServer.put(client,server);
        }
        return client;
    }
    public Coord updateClientSideSize()
    {
        int maxx = 2;
        int maxy = 2;
        for(WItem w : wmap.values())
        {
            Coord wc = sqroff(w.c);
            maxx = Math.max(wc.x,maxx);
            maxy = Math.max(wc.y,maxy);
        }
        this.isz_client = new Coord(maxx+2,maxy+2);
        this.resize(invsz(isz_client));
        return isz_client;
    }
    
    public static Coord sqoff(Coord c) {
	return(c.mul(sqsz).add(ctl.sz()));
    }

    public static Coord sqroff(Coord c) {
	return(c.sub(ctl.sz()).div(sqsz));
    }

    public static Coord invsz(Coord sz) {
	return(sz.mul(sqsz).add(ctl.sz()).add(cbr.sz()).sub(4, 4));
    }

    public static void invrefl(GOut g, Coord c, Coord sz) {
	Coord ul = g.ul.sub(g.ul.div(2)).mod(refl.sz()).inv();
	Coord rc = new Coord();
	for(rc.y = ul.y; rc.y < c.y + sz.y; rc.y += refl.sz().y) {
	    for(rc.x = ul.x; rc.x < c.x + sz.x; rc.x += refl.sz().x) {
		g.image(refl, rc, c, sz);
	    }
	}
    }

    public static void invsq(GOut g, Coord c, Coord sz) {
	for(Coord cc = new Coord(0, 0); cc.y < sz.y; cc.y++) {
	    for(cc.x = 0; cc.x < sz.x; cc.x++) {
		g.image(bsq, c.add(cc.mul(sqsz)).add(ctl.sz()));
	    }
	}
	for(int x = 0; x < sz.x; x++) {
	    g.image(obt, c.add(ctl.sz().x + sqsz.x * x, 0));
	    g.image(obb, c.add(ctl.sz().x + sqsz.x * x, obt.sz().y + (sqsz.y * sz.y) - 4));
	}
	for(int y = 0; y < sz.y; y++) {
	    g.image(obl, c.add(0, ctl.sz().y + sqsz.y * y));
	    g.image(obr, c.add(obl.sz().x + (sqsz.x * sz.x) - 4, ctl.sz().y + sqsz.y * y));
	}
	g.image(ctl, c);
	g.image(ctr, c.add(ctl.sz().x + (sqsz.x * sz.x) - 4, 0));
	g.image(cbl, c.add(0, ctl.sz().y + (sqsz.y * sz.y) - 4));
	g.image(cbr, c.add(cbl.sz().x + (sqsz.x * sz.x) - 4, ctr.sz().y + (sqsz.y * sz.y) - 4));
    }

    public static void invsq(GOut g, Coord c) {
	g.image(sqlite, c);
    }

    @Override
    public boolean mousedown(Coord c, int button) {
//	if(button == 2){
//	    int i = 0;
//	    Coord ct = new Coord();
//	    List<GItem> items = getAll();
//	    Collections.sort(items, GItem.comp);
//	    for(GItem item : items){
//		item.wdgmsg("take", Coord.z);
//		ct.x = i%isz.x;
//		ct.y = i/isz.x;
//		wdgmsg("drop", ct);
//		i++;
//	    }
//	    return true;
//	}
	return super.mousedown(c, button);
    }

    public boolean mousewheel(Coord c, int amount) {
        if(ui.modshift) {
            wdgmsg("xfer", amount);
        }
        return(true);
    }
    public Widget makechild(String type, Object[] pargs, Object[] cargs) {
	Coord c = (Coord)pargs[0];
        c = translateCoordinatesServerClient(c);
	Widget ret = gettype(type).create(c, this, cargs);
	if(ret instanceof GItem) {
	    GItem i = (GItem)ret;
	    wmap.put(i, new WItem(sqoff(c), this, i));
	    newseq++;
            
            if(isTranslated)
                updateClientSideSize();
	}
	return(ret);
    }

    public void cdestroy(Widget w) {
	super.cdestroy(w);
	if(w instanceof GItem) {
	    GItem i = (GItem)w;
            WItem wi = wmap.remove(i);
	    ui.destroy(wi);
	}
    }

    public boolean drop(Coord cc, Coord ul) {
        Coord clientcoords = sqroff(ul.add(isqsz.div(2)));
        Coord servercoords = translateCoordinatesClientServer(clientcoords);
	wdgmsg("drop", servercoords);
	return(true);
    }

    public boolean iteminteract(Coord cc, Coord ul) {
	return(false);
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "sz") {
	    isz = (Coord)args[0];
            if(isTranslated)
            {
                resize(invsz(updateClientSideSize()));
            }
            else
            {
                resize(invsz(isz));
            }
	}
    }
    
    public void wdgmsg(Widget sender, String msg, Object... args) {
	if(msg.equals("transfer-same")){
	    process(getSame((String) args[0],(Boolean)args[1]), "transfer");
	} else if(msg.equals("drop-same")){
	    process(getSame((String) args[0], (Boolean) args[1]), "drop");
	} else {
	    super.wdgmsg(sender, msg, args);
	}
    }

    private void process(List<WItem> items, String action) {
	for (WItem item : items){
	    item.item.wdgmsg(action, Coord.z);
	}
    }

    private List<WItem> getSame(String name, Boolean ascending) {
	List<WItem> items = new ArrayList<WItem>();
	for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
	    if (wdg.visible && wdg instanceof WItem) {
		if (((WItem) wdg).item.resname().equals(name))
		    items.add((WItem) wdg);
	    }
	}
	Collections.sort(items, ascending?cmp_asc:cmp_desc);
	return items;
    }
    
}
