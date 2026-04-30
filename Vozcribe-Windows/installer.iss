[Setup]
AppName=Vozcribe
AppVersion=1.0.2
AppPublisher=Vozcribe
DefaultDirName={autopf}\Vozcribe
DefaultGroupName=Vozcribe
OutputDir=installer-output
OutputBaseFilename=VozcribeSetup
SetupIconFile=Vozcribe\Resources\Icons\tray-icon.ico
UninstallDisplayIcon={app}\Vozcribe.exe
Compression=lzma
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked
Name: "startupentry"; Description: "Start Vozcribe automatically when Windows starts"; GroupDescription: "Startup:"; Flags: unchecked

[Files]
Source: "Vozcribe\publish-output\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Vozcribe"; Filename: "{app}\Vozcribe.exe"; IconFilename: "{app}\Vozcribe.exe"
Name: "{group}\Uninstall Vozcribe"; Filename: "{uninstallexe}"
Name: "{commondesktop}\Vozcribe"; Filename: "{app}\Vozcribe.exe"; Tasks: desktopicon; IconFilename: "{app}\Vozcribe.exe"

[Run]
Filename: "{app}\Vozcribe.exe"; Description: "Launch Vozcribe"; Flags: nowait postinstall skipifsilent

[Registry]
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "Vozcribe"; ValueData: """{app}\Vozcribe.exe"""; Flags: uninsdeletevalue; Tasks: startupentry
