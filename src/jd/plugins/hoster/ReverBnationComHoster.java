//jDownloader - Downloadmanager
//Copyright (C) 2010  JD-Team support@jdownloader.org
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
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "reverbnation.com" }, urls = { "http://reverbnationcomid\\d+reverbnationcomartist\\d+" }, flags = { 0 })
public class ReverBnationComHoster extends PluginForHost {

    public ReverBnationComHoster(final PluginWrapper wrapper) {
        super(wrapper);
    }

    private String PASS = null;

    @Override
    public String getAGBLink() {
        return "http://www.reverbnation.com/main/terms_and_conditions";
    }

    public void correctDownloadLink(DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replace("http://", ""));
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        setBrowserExclusive();
        /* Errorhandling for crippled/old links */
        if (getMainlink(link) == null) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        br.getPage(getMainlink(link));
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        return AvailableStatus.TRUE;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        PASS = br.getRegex("pass: \"([a-z0-9]+)\"").getMatch(0);
        if (PASS == null) {
            logger.warning("Pass is null");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        br.setDebug(true);
        br.setFollowRedirects(false);
        // alternative Downloadmethode
        final Regex infoRegex = new Regex(downloadLink.getDownloadURL(), "reverbnationcomid(\\d+)reverbnationcomartist(\\d+)");
        br.postPage("http://www.reverbnation.com/controller/audio_player/download_song/" + infoRegex.getMatch(0) + "?modal=true", "");
        String dllink = br.getRegex("location\\.href=\\'(.*?)\\'").getMatch(0);
        // der harte Weg
        if (dllink == null) {
            dllink = getDllink(downloadLink);
        }
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        // Probably no correct file
        if (dl.getConnection().getLongContentLength() < 40000) {
            if (downloadLink.getBooleanProperty("downloadstream")) {
                throw new PluginException(LinkStatus.ERROR_FATAL, "This song is not downloadable");
            }
            downloadLink.setProperty("downloadstream", true);
            throw new PluginException(LinkStatus.ERROR_RETRY);
        }
        String amzName = dl.getConnection().getHeaderField("x-amz-meta-filename");
        if (amzName != null) {
            downloadLink.setFinalFileName(amzName);
        } else {
            String name = Plugin.getFileNameFromHeader(dl.getConnection());
            if ("flash9-en.ocx".equals(name)) {
                /* workaround for dirty name */
                String orgName = downloadLink.getStringProperty("orgName", null);
                if (orgName != null) {
                    downloadLink.setFinalFileName(orgName);
                }
            }
        }
        dl.startDownload();
    }

    // private String getBps(final String crap, final String sID) throws PluginException {
    // /*
    // * Ich habe es mit BigInteger versucht!
    // */
    // final String tk = new Regex(crap, "tk=(\\d+)").getMatch(0);
    // final String rk = new Regex(crap, "rk=(\\d+)").getMatch(0);
    // final String keyCode = "1103515245";
    // final String constantKey = "12345";
    // final String binRate = "1.000000E+015";
    // Object result = new Object();
    // final String fun = "(" + rk + "+" + tk + "*" + sID + "*" + keyCode + "+" + constantKey + ")%" + binRate;
    // final ScriptEngineManager manager = jd.plugins.hoster.DummyScriptEnginePlugin.getScriptEngineManager(this);
    // final ScriptEngine engine = manager.getEngineByName("javascript");
    // try {
    // result = ((Double) engine.eval(fun)).longValue();
    // } catch (final Exception e) {
    // logger.log(Level.SEVERE, e.getMessage(), e);
    // throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    // }
    // return result.toString();
    // }

    @SuppressWarnings("deprecation")
    private String getDllink(final DownloadLink link) throws IOException, PluginException {
        String finallink = null;
        final Regex infoRegex = new Regex(link.getDownloadURL(), "reverbnationcomid(\\d+)reverbnationcomartist(\\d+)");
        final String song_id = infoRegex.getMatch(0);
        final String artist_id = infoRegex.getMatch(1);
        if (link.getBooleanProperty("downloadstream")) {
            br.getPage(link.getDownloadURL());
            final String pass = br.getRegex("pass: \"([a-z0-9]+)\"").getMatch(0);
            if (pass == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            br.getPage("http://www.reverbnation.com/audio_player/html_player_stream/" + pass + "?client=234s3rwas&song_id=" + song_id);
            finallink = br.getRedirectLocation();
        } else {
            /*
             * Last revision with old handling: 25939 Modified date: 25.03.2015
             */
            final String song_cookie = PASS + "song-" + song_id;
            br.setCookie(br.getURL(), "_reverb_currentsong", song_cookie);
            br.getPage("http://www.reverbnation.com/controller/audio_player/get_xml/?player=InlineAudioPlayer");
            // br.getHeaders().put("X-CSRF-Token", "xxxxxxxkIobXmDTc=");
            br.getHeaders().put("X-RN-FRAMEWORK-VERSION", "R4.1.003");
            br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            br.getHeaders().put("Referer", "http://www.reverbnation.com/neoclubber/songs");
            br.getHeaders().put("Accept", "*/*");
            br.postPage("http://www.reverbnation.com/audio_player/add_to_beginning/" + song_id + "?from_page_object=artist_" + artist_id, "");
            br.getPage("http://www.reverbnation.com/audio_player/html_player_stream/" + PASS + "?client=234s3rwas&song_id=" + song_id);
            finallink = br.getRedirectLocation();
            if (finallink == null) {
                finallink = br.getRegex("location\\.href = (?:\\'|\")(http://[^<>\"]*?)(?:\\'|\")").getMatch(0);
            }
            if (finallink == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            // wichtige Header
            br.getHeaders().put("Referer", "http://cache.reverbnation.com/audio_player/inline_audioplayer_v2xx.swf?4037");
            br.getHeaders().put("x-flash-version", "10,1,53,64");
            br.getHeaders().put("Pragma", null);
            br.getHeaders().put("Cache-Control", null);
            br.getHeaders().put("Accept", "*/*");
            br.getHeaders().put("Accept-Language", "de-DE");
            br.getHeaders().put("Accept-Charset", null);
            br.getHeaders().put("Connection", "Keep-Alive");
        }
        return finallink;
    }

    private String getMainlink(final DownloadLink dl) {
        return dl.getStringProperty("mainlink", null);
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean allowHandle(final DownloadLink downloadLink, final PluginForHost plugin) {
        return downloadLink.getHost().equalsIgnoreCase(plugin.getHost());
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }
}