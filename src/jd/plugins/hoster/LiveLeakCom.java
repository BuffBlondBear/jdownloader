//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.io.IOException;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "liveleak.com" }, urls = { "http://(www\\.)?liveleakvideodecrypted\\.com/[a-z0-9_]+" })
public class LiveLeakCom extends PluginForHost {
    private String dllink = null;

    public LiveLeakCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://www.liveleak.com/tos";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        setBrowserExclusive();
        br.setFollowRedirects(true);
        br.setCookie("http://liveleak.com/", "liveleak_safe_mode", "0");
        final String linkid = new Regex(downloadLink.getDownloadURL(), "([a-z0-9_]+)$").getMatch(0);
        downloadLink.setLinkID(linkid);
        br.getPage("https://www." + getHost() + "/ll_embed?f=" + linkid);
        if (br.containsHTML("File not found or deleted\\!")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("shareTitle\\s*?:\\s*?\\'([^<>\"\\']+)\\'").getMatch(0);
        if (filename == null) {
            /* Fallback */
            filename = linkid;
        }
        String ext = ".mp4";
        dllink = br.getRegex("source src=\"(http[^<>\"]+)\"[^>]*?label=\"HD\"").getMatch(0);
        if (dllink == null) {
            dllink = br.getRegex("source src=\"(http[^<>\"]+)\"[^>]*?label=").getMatch(0);
        }
        if (dllink == null) {
            ext = ".flv";
            filename = filename.replace(".mp4", "");
            dllink = br.getRegex("file_url=(http[^<>\"]+)\\&").getMatch(0);
        }
        if (filename.contains(".")) {
            String oldExt = filename.substring(filename.lastIndexOf("."));
            if (oldExt != null && oldExt.length() <= 5) {
                filename = filename.replace(oldExt, "");
            }
        }
        if (filename == null || dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dllink = Encoding.htmlDecode(dllink);
        filename = filename.trim();
        downloadLink.setFinalFileName(Encoding.htmlDecode(filename) + ext);
        final Browser br2 = br.cloneBrowser();
        // In case the link redirects to the finallink
        br2.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            con = br2.openGetConnection(dllink);
            if (!con.getContentType().contains("html")) {
                downloadLink.setDownloadSize(con.getLongContentLength());
            } else {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            return AvailableStatus.TRUE;
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
        }
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public void resetPluginGlobals() {
    }
}