; Inno Setup script for the CodeQuest installer.
; Build: see tools/installer/build-installer.ps1 (it builds the jar, makes the app folder with jpackage, then runs this).
#ifndef AppName
  #define AppName "CodeQuest"
#endif
#ifndef AppGuid
  #define AppGuid "6B1B0E52-7C3A-4C58-9E0D-C0DE9E57A11D"
#endif
; build-installer.ps1 passes the version from gradle.properties
#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#define Publisher "The Wolves"
#ifndef SourceDir
  #define SourceDir "..\..\build\installer\image\CodeQuest"
#endif
#ifndef OutDir
  #define OutDir "..\..\build\installer\out"
#endif

[Setup]
AppId={{{#AppGuid}}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#Publisher}
; installs for the current user only, so it needs no administrator password
PrivilegesRequired=lowest
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
OutputDir={#OutDir}
OutputBaseFilename=CodeQuest-Setup-{#AppVersion}
SetupIconFile=..\..\lwjgl3\icons\logo.ico
UninstallDisplayIcon={app}\CodeQuest.exe
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
; the wolf on the welcome and finish pages, the shield in the small header picture (several sizes for sharp display scaling)
WizardImageFile=wizard_164.bmp,wizard_192.bmp,wizard_246.bmp,wizard_328.bmp
WizardSmallImageFile=wizard_small_55.bmp,wizard_small_64.bmp,wizard_small_80.bmp,wizard_small_110.bmp
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"

[InstallDelete]
; earlier versions named the jar after the version: do not leave the old ones behind
Type: files; Name: "{app}\app\*.jar"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\CodeQuest.exe"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\CodeQuest.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\CodeQuest.exe"; Description: "Start CodeQuest"; Flags: nowait postinstall skipifsilent
; the game's own updater runs the installer silently with /UPDATE: open the new version again
Filename: "{app}\CodeQuest.exe"; Flags: nowait; Check: IsUpdate

[Code]
function IsUpdate: Boolean;
var
  I: Integer;
begin
  Result := False;
  for I := 1 to ParamCount do
    if CompareText(ParamStr(I), '/UPDATE') = 0 then
      Result := True;
end;
