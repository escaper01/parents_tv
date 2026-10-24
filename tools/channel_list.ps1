# The channels Parents TV ships with, in TV order, group by group. Loaded by build_channels.ps1.
# Each entry: display name, group, and a regex matched against playlist names (after quality and
# marker tags are stripped). Optional: `first` = URLs tried before anything found in the
# playlists, `exclude` = regex of URLs to drop, `allowGeo` = keep entries marked geo-blocked.
# Keep this file pure ASCII (Windows PowerShell 5 reads BOM-less files as ANSI).

$wanted = @(
    # --- Morocco (always first) --------------------------------------------------------------
    @{ name = '2M';                 group = 'Morocco'; match = '^2M$' }
    @{ name = '2M Monde';           group = 'Morocco'; match = '^2M Monde( \+1)?$' }
    # The server's master playlist declares no audio codec, so the player would drop the sound;
    # its media playlist does not have that problem.
    @{ name = 'Al Maghribia';       group = 'Morocco'; match = '^Al Maghribia$'; first = @('http://185.9.2.18/chid_205/tracks-v1a1/mono.m3u8') }
    @{ name = 'Arryadia';           group = 'Morocco'; match = '^Arryadia$' }
    @{ name = 'Chada TV';           group = 'Morocco'; match = '^Chada TV$' }
    @{ name = 'Medi1TV Maghreb';    group = 'Morocco'; match = '^Medi1TV Maghreb$' }
    @{ name = 'Medi1TV Arabic';     group = 'Morocco'; match = '^Medi1TV Arabic$' }
    @{ name = 'Medi1TV Afrique';    group = 'Morocco'; match = '^Medi1TV Afrique$' }
    @{ name = 'StoryChannel TV';    group = 'Morocco'; match = '^StoryChannel TV$' }
    # --- Arabic news -------------------------------------------------------------------------
    # iptv-org marks RT Arabic geo-blocked (it is blocked in the EU), but it plays from Morocco.
    @{ name = 'RT Arabic';          group = 'Arabic News'; match = '^RT Arabic$'; allowGeo = $true }
    @{ name = 'France 24 Arabic';   group = 'Arabic News'; match = '^France 24 Arabic$' }
    # Free-TV files some Al Jazeera English links under plain "Al Jazeera".
    @{ name = 'Al Jazeera';         group = 'Arabic News'; match = '^Al Jazeera$'; exclude = '/AJE/|english' }
    @{ name = 'Al Jazeera Mubasher';group = 'Arabic News'; match = '^Al Jazeera Mubasher$' }
    @{ name = 'Al Arabiya';         group = 'Arabic News'; match = '^Alarabiya$|^Al Arabiya$' }
    @{ name = 'Al Hadath';          group = 'Arabic News'; match = '^Al Hadath$' }
    @{ name = 'Sky News Arabia';    group = 'Arabic News'; match = '^Sky News Arabia$' }
    @{ name = 'Asharq News';        group = 'Arabic News'; match = '^Asharq News$' }
    @{ name = 'Al Araby TV';        group = 'Arabic News'; match = '^Al Araby TV$' }
    @{ name = 'Al Ghad TV';         group = 'Arabic News'; match = '^Al Ghad TV$' }
    @{ name = 'Al Mayadeen';        group = 'Arabic News'; match = '^Al Mayadeen TV$' }
    @{ name = 'BBC Arabic';         group = 'Arabic News'; match = '^BBC Arabic$' }
    @{ name = 'TRT Arabi';          group = 'Arabic News'; match = '^TRT Arabi$' }
    @{ name = 'DW Arabic';          group = 'Arabic News'; match = '^DW Arabic$' }
    @{ name = 'CGTN Arabic';        group = 'Arabic News'; match = '^CGTN Arabic$' }
    # --- Documentary -------------------------------------------------------------------------
    @{ name = 'Nat Geo Abu Dhabi';  group = 'Documentary'; match = '^National Geographic Abu Dhabi$' }
    @{ name = 'Asharq Documentary'; group = 'Documentary'; match = '^Asharq Documentary$' }
    @{ name = 'Asharq Discovery';   group = 'Documentary'; match = '^Asharq Discovery$' }
    @{ name = 'Al Arabiya Programs';group = 'Documentary'; match = '^Al Arabiya Programs$' }
    @{ name = 'BBC Earth';          group = 'Documentary'; match = '^BBC Earth$|^BBC Earth US$' }
    @{ name = 'Documentary+';       group = 'Documentary'; match = '^Documentary\+( International)?$' }
    @{ name = 'DocuBay';            group = 'Documentary'; match = '^DocuBay TV$' }
    @{ name = 'History Hit';        group = 'Documentary'; match = '^History Hit$' }
    @{ name = 'Autentic History';   group = 'Documentary'; match = '^Autentic History$' }
    @{ name = 'Military History';   group = 'Documentary'; match = '^Military History$' }
    @{ name = 'CGTN Documentary';   group = 'Documentary'; match = '^CGTN Documentary$' }
    @{ name = 'Terra Mater WILD';   group = 'Documentary'; match = '^Terra Mater WILD English$' }
    @{ name = 'INWILD';             group = 'Documentary'; match = '^INWILD$' }
    @{ name = 'WildEarth';          group = 'Documentary'; match = '^WildEarth$' }
    @{ name = 'Curiosity NOW';      group = 'Documentary'; match = '^Curiosity NOW EN$' }
    @{ name = 'MagellanTV';         group = 'Documentary'; match = '^MagellanTV Now$' }
    @{ name = 'Docurama';           group = 'Documentary'; match = '^Docurama$' }
    @{ name = 'NatureTime UK';      group = 'Documentary'; match = '^NatureTime UK$' }
    @{ name = 'NatureTime France';  group = 'Documentary'; match = '^NatureTime France$' }
    @{ name = 'Cap Terre';          group = 'Documentary'; match = '^Cap Terre$' }
    @{ name = 'Pluto TV Histoire';  group = 'Documentary'; match = '^Pluto TV Histoire$' }
    @{ name = 'Pluto TV Animaux';   group = 'Documentary'; match = '^Pluto TV Animaux$' }
    @{ name = 'Pluto TV Aventure';  group = 'Documentary'; match = '^Pluto TV Aventure$' }
    @{ name = 'Pluto TV Science';   group = 'Documentary'; match = '^Pluto TV Science \(United States\)$' }
    @{ name = 'Pluto TV History';   group = 'Documentary'; match = '^Pluto TV History$' }
    # --- Movies (Arabic, French, English) ----------------------------------------------------
    @{ name = 'Aflam';              group = 'Movies'; match = '^Aflam$' }
    @{ name = 'iFilm Arabic';       group = 'Movies'; match = '^iFilm Arabic$' }
    @{ name = 'Mix Hollywood';      group = 'Movies'; match = '^Mix Hollywood$' }
    @{ name = 'Pluto TV Cine';      group = 'Movies'; match = '^Cine by Pluto TV$' }
    @{ name = 'Cine Action';        group = 'Movies'; match = '^Cine Action by Pluto TV$' }
    @{ name = 'Cine Thrillers';     group = 'Movies'; match = '^Cine Thrillers by Pluto TV$' }
    @{ name = 'Cine Romance';       group = 'Movies'; match = '^Cine Romance by Pluto TV$' }
    @{ name = 'Cine Retro';         group = 'Movies'; match = '^Cine Retro by Pluto TV$' }
    @{ name = 'Cine Nanar';         group = 'Movies'; match = '^Cine Nanar$' }
    @{ name = 'Cine Western';       group = 'Movies'; match = '^Cine Western \(France\)$' }
    @{ name = 'Sony One Hits Action';  group = 'Movies'; match = '^Sony One Hits Action$' }
    @{ name = 'Sony One Hits Comedie'; group = 'Movies'; match = '^Sony One Hits Comedie$' }
    @{ name = 'Sony One Favoris';   group = 'Movies'; match = '^Sony One Favoris$' }
    @{ name = 'Emotion L';          group = 'Movies'; match = '^Emotion L$' }
    @{ name = 'Rakuten Top Movies'; group = 'Movies'; match = '^Rakuten TV Top Movies UK$' }
    @{ name = 'Rakuten Action Movies'; group = 'Movies'; match = '^Rakuten TV Action Movies UK$' }
    @{ name = 'Rakuten Drama Movies';  group = 'Movies'; match = '^Rakuten TV Drama Movies UK$' }
    @{ name = 'Rakuten Comedy Movies'; group = 'Movies'; match = '^Rakuten TV Comedy Movies UK$' }
    @{ name = 'Movies Action';      group = 'Movies'; match = '^Movies Action$' }
    @{ name = 'Movies Thriller';    group = 'Movies'; match = '^Movies Thriller$' }
    @{ name = 'MovieSphere';        group = 'Movies'; match = '^MovieSphere( UK)?$' }
    @{ name = 'GREAT! movies';      group = 'Movies'; match = '^GREAT! movies$' }
    @{ name = 'Gravitas Movies';    group = 'Movies'; match = '^Gravitas Movies$' }
    @{ name = 'Runtime';            group = 'Movies'; match = '^Runtime$' }
    @{ name = 'Classic Movies';     group = 'Movies'; match = '^Classic Movies Channel$' }
    @{ name = 'Pluto TV Action Movies'; group = 'Movies'; match = '^Pluto TV Action Movies$' }
    @{ name = 'Pluto TV Westerns';  group = 'Movies'; match = '^Pluto TV Westerns \(United States\)$' }
    # --- Series and drama --------------------------------------------------------------------
    @{ name = 'MBC Drama';          group = 'Series'; match = '^MBC Drama \(United Arab Emirates\)$|^MBC\+ Drama$' }
    @{ name = 'MBC Masr Drama';     group = 'Series'; match = '^MBC Masr Drama$' }
    @{ name = 'ON Drama';           group = 'Series'; match = '^ON Vie Drama$' }
    @{ name = 'Nile Drama';         group = 'Series'; match = '^Nile Drama$' }
    @{ name = 'Bab Al Hara';        group = 'Series'; match = '^Bab Al Hara$' }
    @{ name = 'Maraya';             group = 'Series'; match = '^Maraya$' }
    # --- General entertainment (popular on Nilesat) ------------------------------------------
    @{ name = 'MBC 1';              group = 'Entertainment'; match = '^MBC 1$|^MBC$' }
    @{ name = 'MBC 4';              group = 'Entertainment'; match = '^MBC 4$' }
    @{ name = 'MBC 5';              group = 'Entertainment'; match = '^MBC 5$' }
    @{ name = 'MBC Masr';           group = 'Entertainment'; match = '^MBC Masr$' }
    @{ name = 'MBC Masr 2';         group = 'Entertainment'; match = '^MBC Masr 2$' }
    @{ name = 'MBC Iraq';           group = 'Entertainment'; match = '^MBC Iraq$' }
    @{ name = 'Dubai TV';           group = 'Entertainment'; match = '^Dubai TV( International)?$' }
    @{ name = 'Abu Dhabi TV';       group = 'Entertainment'; match = '^Abu Dhabi TV$' }
    @{ name = 'Sharjah TV';         group = 'Entertainment'; match = '^Sharjah TV$' }
    @{ name = 'Ajman TV';           group = 'Entertainment'; match = '^Ajman TV$' }
    @{ name = 'Fujairah TV';        group = 'Entertainment'; match = '^Fujairah TV$' }
    @{ name = 'Al Masriya';         group = 'Entertainment'; match = '^Al Masriyah$' }
    @{ name = 'Al Saudiya';         group = 'Entertainment'; match = '^Al Saudiya$' }
    @{ name = 'SBC';                group = 'Entertainment'; match = '^SBC( TV)?$' }
    @{ name = 'Qatar TV';           group = 'Entertainment'; match = '^Qatar Television$' }
    @{ name = 'Al Rayyan TV';       group = 'Entertainment'; match = '^Al Rayyan TV$' }
    @{ name = 'KTV 1';              group = 'Entertainment'; match = '^KTV 1$' }
    @{ name = 'Oman TV';            group = 'Entertainment'; match = '^Oman TV$' }
    @{ name = 'Bahrain TV';         group = 'Entertainment'; match = '^Bahrain TV$' }
    @{ name = 'LBC International';  group = 'Entertainment'; match = '^LBC International$|^LBC$' }
    @{ name = 'MTV Lebanon';        group = 'Entertainment'; match = '^MTV Lebanon$' }
    @{ name = 'Al Jadeed';          group = 'Entertainment'; match = '^Al Jadeed$' }
    @{ name = 'OTV';                group = 'Entertainment'; match = '^OTV \(Lebanon\)$' }
    @{ name = 'Future TV';          group = 'Entertainment'; match = '^Future TV$' }
    @{ name = 'Roya TV';            group = 'Entertainment'; match = '^Roya TV \(Jordan\)$' }
    @{ name = 'Jordan TV';          group = 'Entertainment'; match = '^Jordan TV$' }
    @{ name = 'Palestine TV';       group = 'Entertainment'; match = '^Palestine Satellite Channel$' }
    @{ name = 'Syria TV';           group = 'Entertainment'; match = '^Syria TV$' }
    @{ name = 'Al Iraqia';          group = 'Entertainment'; match = '^Al Iraqia$' }
    @{ name = 'Al Sharqiya';        group = 'Entertainment'; match = '^Al Sharqiya$' }
    @{ name = 'El Watania 1';       group = 'Entertainment'; match = '^El Watania 1$' }
    @{ name = 'Libya Al Wataniya';  group = 'Entertainment'; match = '^Libya Al Wataniya$' }
    # --- International news ------------------------------------------------------------------
    @{ name = 'France 24';          group = 'International News'; match = '^France 24$' }
    @{ name = 'France 24 English';  group = 'International News'; match = '^France 24 English( HD)?$' }
    @{ name = 'Al Jazeera English'; group = 'International News'; match = '^Al Jazeera English( HD)?$' }
    @{ name = 'BBC News';           group = 'International News'; match = '^BBC News UK HD$|^BBC News North America$|^BBC News Europe$' }
    @{ name = 'Fox News';           group = 'International News'; match = '^Fox News Channel$' }
    # --- Sports (free-to-air only; beIN is a paid network and is left out) -------------------
    @{ name = 'KTV Sport';          group = 'Sports'; match = '^KTV Sport$' }
    @{ name = 'KTV Sport Plus';     group = 'Sports'; match = '^KTV Sport Plus$' }
    @{ name = 'Oman Sports';        group = 'Sports'; match = '^Oman Sports TV$' }
    @{ name = 'El Heddaf TV';       group = 'Sports'; match = '^El-Heddaf TV$' }
    @{ name = 'Al Iraqia Sport';    group = 'Sports'; match = '^Al Iraqia Sport$' }
    @{ name = 'Bahrain Sports';     group = 'Sports'; match = '^Bahrain Sports 1$' }
    # --- Music -------------------------------------------------------------------------------
    @{ name = 'Mazzika';            group = 'Music'; match = '^Mazzika$' }
    @{ name = 'Arabica TV';         group = 'Music'; match = '^Arabica TV$' }
    @{ name = 'Aghani Aghani';      group = 'Music'; match = '^Aghani Aghani TV$' }
    @{ name = 'Tarab';              group = 'Music'; match = '^Tarab$' }
    @{ name = 'Fairuz';             group = 'Music'; match = '^Fairuz$' }
    @{ name = 'Mohammed Abdo';      group = 'Music'; match = '^Mohammed Abdo$' }
    @{ name = 'Abdulmajeed Abdullah'; group = 'Music'; match = '^Abdulmajeed Abdullah$' }
    @{ name = 'Jordan Songs';       group = 'Music'; match = '^Jordan Songs$' }
    @{ name = 'MFM';                group = 'Music'; match = '^mfm$' }
    # --- Kids --------------------------------------------------------------------------------
    @{ name = 'Spacetoon';          group = 'Kids'; match = '^Spacetoon Arabic$' }
    @{ name = 'ON Kids';            group = 'Kids'; match = '^ON Kids$' }
    @{ name = 'Koogi TV';           group = 'Kids'; match = '^Koogi TV$' }
    @{ name = 'Atfal & Mawaheb';    group = 'Kids'; match = '^Atfal & Mawaheb TV$' }
    @{ name = 'Amou Yazid TV';      group = 'Kids'; match = '^Amou Yazid TV$' }
    @{ name = 'Taha TV';            group = 'Kids'; match = '^Taha TV$' }
    # --- Culture -----------------------------------------------------------------------------
    @{ name = 'Saudi Thaqafiya';    group = 'Culture'; match = '^Saudi Thaqafiya TV$' }
    @{ name = 'Oman TV Cultural';   group = 'Culture'; match = '^Oman TV Cultural$' }
    @{ name = 'KTV Arabe';          group = 'Culture'; match = '^KTV Arabe$' }
    # --- Religious ---------------------------------------------------------------------------
    @{ name = 'Al Quran Al Kareem'; group = 'Religious'; match = '^Al Quran Al Kareem TV$' }
    @{ name = 'Iqraa';              group = 'Religious'; match = '^Iqraa Arabic$' }
)
