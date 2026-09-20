#ifndef UNICODE
#define UNICODE
#endif

#ifndef _UNICODE
#define _UNICODE
#endif

#ifndef NOMINMAX
#define NOMINMAX
#endif

#include <windows.h>
#include <objbase.h>
#include <objidl.h>
#include <ole2.h>

#include <gdiplus.h>

#include <windowsx.h>
#include <winhttp.h>
#include <shellapi.h>
#include <dwmapi.h>

#include <filesystem>
#include <fstream>
#include <string>
#include <vector>
#include <thread>
#include <atomic>
#include <sstream>
#include <algorithm>
#include <cmath>
#include <chrono>
#include <iomanip>
#include <system_error>
#include <cstdio>
#include <iterator>
#pragma comment(lib, "winhttp.lib")
#pragma comment(lib, "gdiplus.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "dwmapi.lib")
#pragma comment(lib, "ole32.lib")

namespace fs = std::filesystem;

// ============================================================
// GDI+ ALIASES
// ============================================================

using Gdiplus::Color;
using Gdiplus::Font;
using Gdiplus::FontFamily;
using Gdiplus::Graphics;
using Gdiplus::GraphicsPath;
using Gdiplus::LinearGradientBrush;
using Gdiplus::LinearGradientModeHorizontal;
using Gdiplus::Pen;
using Gdiplus::RectF;
using Gdiplus::SolidBrush;
using Gdiplus::StringFormat;

using Real = Gdiplus::REAL;

// ============================================================
// GDI+ ENUMS
// ============================================================
//
// UWAGA:
// StringAlignmentCenter / Near oraz FontStyleRegular / Bold
// są enumami w namespace Gdiplus, a nie klasami.
// Dlatego używamy ich jako:
//     Gdiplus::StringAlignmentCenter
//     Gdiplus::StringAlignmentNear
//     Gdiplus::FontStyleRegular
//     Gdiplus::FontStyleBold
//
// Nie używamy:
//     Gdiplus::StringAlignment::StringAlignmentCenter
//     Gdiplus::FontStyle::FontStyleBold
//

static constexpr Gdiplus::StringAlignment ALIGN_NEAR =
Gdiplus::StringAlignmentNear;

static constexpr Gdiplus::StringAlignment ALIGN_CENTER =
Gdiplus::StringAlignmentCenter;

static constexpr INT FONT_REGULAR =
Gdiplus::FontStyleRegular;

static constexpr INT FONT_BOLD =
Gdiplus::FontStyleBold;

// ============================================================
// HELPER DO KONWERSJI INT -> REAL
// ============================================================

static inline Real R(int value)
{
    return static_cast<Real>(value);
}

static inline Real R(float value)
{
    return static_cast<Real>(value);
}

static inline Real R(double value)
{
    return static_cast<Real>(value);
}
// ============================================================
// APP
// ============================================================

static constexpr wchar_t APP_NAME[] = L"Zarex Loader";

static constexpr wchar_t MC_VERSION[] = L"1.21.11";
static constexpr wchar_t FABRIC_LOADER[] = L"0.18.1";

static constexpr wchar_t FABRIC_INSTALLER_URL[] =
L"https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.3/"
L"fabric-installer-1.0.3.jar";

static constexpr wchar_t FABRIC_API_URL[] =
L"https://cdn.modrinth.com/data/P7dR8mSH/versions/"
L"6qAuTtLR/fabric-api-0.141.6%2B1.21.11.jar";

static constexpr wchar_t ZAREX_MOD_URL[] =
L"https://cdn.discordapp.com/attachments/1547863897715908669/"
L"1551245396561371156/zarex-client-1.0.0.jar?"
L"ex=6ab1455b&is=6aaff3db&hm="
L"78919bfca64b4aef8a7bb7d533442022e487ac58fd2de1a1569b0909db4539d3&";

// ============================================================
// FIREBASE
// ============================================================

static constexpr wchar_t FIREBASE_API_KEY[] =
L"AIzaSyCxjjZvb5bM-tThiLJUGVb8vp3b_FGpaJQ";

static constexpr wchar_t FIREBASE_DB_URL[] =
L"https://zarex-client-default-rtdb.firebaseio.com";

static constexpr wchar_t FIREBASE_SIGNIN_URL[] =
L"https://identitytoolkit.googleapis.com/v1/accounts:"
L"signInWithPassword?key=";

// ============================================================
// CUSTOM WINDOWS MESSAGES
// ============================================================

static constexpr UINT WM_APP_STATUS =
WM_APP + 1;

static constexpr UINT WM_APP_LOGIN_FINISHED =
WM_APP + 2;

static constexpr UINT WM_APP_LOAD_FINISHED =
WM_APP + 3;

// ============================================================
// GLOBAL STATE
// ============================================================

static ULONG_PTR g_gdiplus = 0;

static HWND g_hwnd = nullptr;

static int g_screen = 0;
// 0 = login
// 1 = menu
// 2 = loading

static bool g_userFocus = false;
static bool g_passFocus = false;
static bool g_save = true;

static std::wstring g_user;
static std::wstring g_pass;

static std::wstring g_status = L"Loading";

static std::wstring g_firebaseToken;
static std::wstring g_uid;
static std::wstring g_licenseExpires;
static std::wstring g_createdAt;

static std::atomic<bool> g_working{ false };
static std::atomic<bool> g_stop{ false };

static std::atomic<int> g_progress{ 0 };
static std::atomic<int> g_spinner{ 0 };


static std::thread g_worker;

// ============================================================
// MODERN UI / DOUBLE BUFFERING
// ============================================================

enum class UIHoverTarget
{
    None,
    Username,
    Password,
    Login,
    Save,
    Load
};

static UIHoverTarget g_hoverTarget = UIHoverTarget::None;
static UIHoverTarget g_pressedTarget = UIHoverTarget::None;
static int g_mouseX = -1;
static int g_mouseY = -1;

static HDC g_backDC = nullptr;
static HBITMAP g_backBitmap = nullptr;
static HBITMAP g_backOldBitmap = nullptr;
static int g_backWidth = 0;
static int g_backHeight = 0;

static void DestroyBackBuffer()
{
    if (g_backDC)
    {
        if (g_backOldBitmap)
        {
            SelectObject(g_backDC, g_backOldBitmap);
            g_backOldBitmap = nullptr;
        }

        if (g_backBitmap)
        {
            DeleteObject(g_backBitmap);
            g_backBitmap = nullptr;
        }

        DeleteDC(g_backDC);
        g_backDC = nullptr;
    }

    g_backWidth = 0;
    g_backHeight = 0;
}

static bool EnsureBackBuffer(HDC windowDC, int width, int height)
{
    if (width <= 0 || height <= 0)
        return false;

    if (g_backDC &&
        g_backWidth == width &&
        g_backHeight == height)
    {
        return true;
    }

    DestroyBackBuffer();

    g_backDC = CreateCompatibleDC(windowDC);
    if (!g_backDC)
        return false;

    g_backBitmap = CreateCompatibleBitmap(windowDC, width, height);
    if (!g_backBitmap)
    {
        DestroyBackBuffer();
        return false;
    }

    g_backOldBitmap =
        static_cast<HBITMAP>(SelectObject(g_backDC, g_backBitmap));

    g_backWidth = width;
    g_backHeight = height;

    return true;
}

// ============================================================
// COLORS
// ============================================================

static Color C(
    BYTE alpha,
    BYTE red,
    BYTE green,
    BYTE blue)
{
    return Color(
        alpha,
        red,
        green,
        blue
    );
}

static Color C(
    BYTE red,
    BYTE green,
    BYTE blue)
{
    return Color(
        255,
        red,
        green,
        blue
    );
}

// ============================================================
// UTF-8 / UTF-16
// ============================================================

static std::string Utf8(
    const std::wstring& value)
{
    if (value.empty())
        return {};

    const int required =
        WideCharToMultiByte(
            CP_UTF8,
            WC_ERR_INVALID_CHARS,
            value.data(),
            static_cast<int>(value.size()),
            nullptr,
            0,
            nullptr,
            nullptr
        );

    if (required <= 0)
        return {};

    std::string result(
        static_cast<size_t>(required),
        '\0'
    );

    WideCharToMultiByte(
        CP_UTF8,
        WC_ERR_INVALID_CHARS,
        value.data(),
        static_cast<int>(value.size()),
        result.data(),
        required,
        nullptr,
        nullptr
    );

    return result;
}

static std::wstring Wide(
    const std::string& value)
{
    if (value.empty())
        return {};

    const int required =
        MultiByteToWideChar(
            CP_UTF8,
            MB_ERR_INVALID_CHARS,
            value.data(),
            static_cast<int>(value.size()),
            nullptr,
            0
        );

    if (required <= 0)
        return {};

    std::wstring result(
        static_cast<size_t>(required),
        L'\0'
    );

    MultiByteToWideChar(
        CP_UTF8,
        MB_ERR_INVALID_CHARS,
        value.data(),
        static_cast<int>(value.size()),
        result.data(),
        required
    );

    return result;
}

// ============================================================
// JSON
// ============================================================

static std::string JsonString(
    const std::string& json,
    const std::string& key)
{
    const std::string needle =
        "\"" + key + "\"";

    size_t p =
        json.find(needle);

    if (p == std::string::npos)
        return {};

    p =
        json.find(
            ':',
            p + needle.size()
        );

    if (p == std::string::npos)
        return {};

    p =
        json.find_first_not_of(
            " \t\r\n",
            p + 1
        );

    if (p == std::string::npos ||
        json[p] != '"')
    {
        return {};
    }

    ++p;

    std::string result;

    result.reserve(64);

    bool escaped = false;

    for (; p < json.size(); ++p)
    {
        const char c = json[p];

        if (escaped)
        {
            switch (c)
            {
            case '"':
                result.push_back('"');
                break;

            case '\\':
                result.push_back('\\');
                break;

            case '/':
                result.push_back('/');
                break;

            case 'b':
                result.push_back('\b');
                break;

            case 'f':
                result.push_back('\f');
                break;

            case 'n':
                result.push_back('\n');
                break;

            case 'r':
                result.push_back('\r');
                break;

            case 't':
                result.push_back('\t');
                break;

            default:
                result.push_back(c);
                break;
            }

            escaped = false;
            continue;
        }

        if (c == '\\')
        {
            escaped = true;
            continue;
        }

        if (c == '"')
            break;

        result.push_back(c);
    }

    return result;
}

static bool JsonBool(
    const std::string& json,
    const std::string& key,
    bool fallback = false)
{
    const std::string needle =
        "\"" + key + "\"";

    size_t p =
        json.find(needle);

    if (p == std::string::npos)
        return fallback;

    p =
        json.find(
            ':',
            p + needle.size()
        );

    if (p == std::string::npos)
        return fallback;

    p =
        json.find_first_not_of(
            " \t\r\n",
            p + 1
        );

    if (p == std::string::npos)
        return fallback;

    if (json.compare(
        p,
        4,
        "true") == 0)
    {
        return true;
    }

    if (json.compare(
        p,
        5,
        "false") == 0)
    {
        return false;
    }

    return fallback;
}

static std::string JsonEscape(
    const std::string& input)
{
    std::string output;

    output.reserve(
        input.size() + 16
    );

    for (unsigned char c : input)
    {
        switch (c)
        {
        case '"':
            output += "\\\"";
            break;

        case '\\':
            output += "\\\\";
            break;

        case '\b':
            output += "\\b";
            break;

        case '\f':
            output += "\\f";
            break;

        case '\n':
            output += "\\n";
            break;

        case '\r':
            output += "\\r";
            break;

        case '\t':
            output += "\\t";
            break;

        default:
            if (c < 0x20)
            {
                char buffer[8]{};

                sprintf_s(
                    buffer,
                    "\\u%04x",
                    c
                );

                output += buffer;
            }
            else
            {
                output.push_back(
                    static_cast<char>(c)
                );
            }

            break;
        }
    }

    return output;
}

// ============================================================
// PATHS
// ============================================================

static fs::path PublicZarex()
{
    wchar_t buffer[MAX_PATH]{};

    const DWORD length =
        GetEnvironmentVariableW(
            L"PUBLIC",
            buffer,
            MAX_PATH
        );

    if (!length ||
        length >= MAX_PATH)
    {
        return L"C:\\Users\\Public\\Zarex";
    }

    return fs::path(buffer) /
        L"Zarex";
}

static fs::path TempGame()
{
    wchar_t buffer[MAX_PATH]{};

    const DWORD length =
        GetTempPathW(
            MAX_PATH,
            buffer
        );

    fs::path base;

    if (length &&
        length < MAX_PATH)
    {
        base = fs::path(buffer);
    }
    else
    {
        base =
            fs::temp_directory_path();
    }

    return base /
        L"ZarexMinecraft" /
        L".minecraft";
}

// ============================================================
// WINDOW
// ============================================================

static void SetWindowClientSize(
    int width,
    int height)
{
    if (!g_hwnd)
        return;

    RECT rect{
        0,
        0,
        width,
        height
    };

    AdjustWindowRectEx(
        &rect,
        WS_POPUP,
        FALSE,
        WS_EX_APPWINDOW
    );

    const int windowWidth =
        rect.right - rect.left;

    const int windowHeight =
        rect.bottom - rect.top;

    RECT workArea{};

    SystemParametersInfoW(
        SPI_GETWORKAREA,
        0,
        &workArea,
        0
    );

    const int x =
        workArea.left +
        ((workArea.right -
            workArea.left) -
            windowWidth) / 2;

    const int y =
        workArea.top +
        ((workArea.bottom -
            workArea.top) -
            windowHeight) / 2;

    SetWindowPos(
        g_hwnd,
        HWND_TOP,
        x,
        y,
        windowWidth,
        windowHeight,
        SWP_NOACTIVATE
    );
}

static void RoundWindow()
{
    if (!g_hwnd)
        return;

    RECT rect{};

    GetClientRect(
        g_hwnd,
        &rect
    );

    HRGN region =
        CreateRoundRectRgn(
            0,
            0,
            rect.right + 1,
            rect.bottom + 1,
            36,
            36
        );

    if (region)
    {
        SetWindowRgn(
            g_hwnd,
            region,
            TRUE
        );
    }
}

// ============================================================
// UI MESSAGE HELPERS
// ============================================================

static void PostStatus(
    const std::wstring& status)
{
    if (!g_hwnd)
        return;

    auto* text =
        new std::wstring(status);

    if (!PostMessageW(
        g_hwnd,
        WM_APP_STATUS,
        0,
        reinterpret_cast<LPARAM>(text)))
    {
        delete text;
    }
}

struct OperationResult
{
    bool success = false;
    std::wstring error;
};

static void PostOperationResult(
    UINT message,
    bool success,
    const std::wstring& error = {})
{
    if (!g_hwnd)
        return;

    auto* result =
        new OperationResult;

    result->success = success;
    result->error = error;

    if (!PostMessageW(
        g_hwnd,
        message,
        0,
        reinterpret_cast<LPARAM>(result)))
    {
        delete result;
    }
}

// ============================================================
// LICENSE
// ============================================================

static bool LicenseDateValid(
    const std::string& iso)
{
    if (iso.size() < 19)
        return false;

    SYSTEMTIME st{};

    GetSystemTime(&st);

    wchar_t current[32]{};

    swprintf_s(
        current,
        L"%04u-%02u-%02uT%02u:%02u:%02uZ",
        st.wYear,
        st.wMonth,
        st.wDay,
        st.wHour,
        st.wMinute,
        st.wSecond
    );

    const std::string now =
        Utf8(current);

    if (now.size() < 19)
        return false;

    return
        iso.substr(0, 19) >
        now.substr(0, 19);
}

// ============================================================
// HTTP
// ============================================================

static bool HttpRequest(
    const std::wstring& url,
    const std::wstring& method,
    const std::string& body,
    std::string& response,
    DWORD& status)
{
    response.clear();
    status = 0;

    URL_COMPONENTS components{};

    components.dwStructSize =
        sizeof(components);

    wchar_t host[512]{};
    wchar_t path[8192]{};
    wchar_t extra[8192]{};

    components.lpszHostName = host;
    components.dwHostNameLength = 511;

    components.lpszUrlPath = path;
    components.dwUrlPathLength = 8191;

    components.lpszExtraInfo = extra;
    components.dwExtraInfoLength = 8191;

    if (!WinHttpCrackUrl(
        url.c_str(),
        0,
        0,
        &components))
    {
        return false;
    }

    HINTERNET session =
        WinHttpOpen(
            L"ZarexLoader/2.0",
            WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY,
            WINHTTP_NO_PROXY_NAME,
            WINHTTP_NO_PROXY_BYPASS,
            0
        );

    if (!session)
        return false;

    WinHttpSetTimeouts(
        session,
        10000,
        10000,
        30000,
        30000
    );

    HINTERNET connection =
        WinHttpConnect(
            session,
            host,
            components.nPort,
            0
        );

    if (!connection)
    {
        WinHttpCloseHandle(session);
        return false;
    }

    const std::wstring object =
        std::wstring(path) +
        std::wstring(extra);

    DWORD flags = 0;

    if (components.nScheme ==
        INTERNET_SCHEME_HTTPS)
    {
        flags |= WINHTTP_FLAG_SECURE;
    }

    HINTERNET request =
        WinHttpOpenRequest(
            connection,
            method.c_str(),
            object.c_str(),
            nullptr,
            WINHTTP_NO_REFERER,
            WINHTTP_DEFAULT_ACCEPT_TYPES,
            flags
        );

    if (!request)
    {
        WinHttpCloseHandle(connection);
        WinHttpCloseHandle(session);
        return false;
    }

    WinHttpSetTimeouts(
        request,
        10000,
        10000,
        30000,
        30000
    );

    const wchar_t* headers =
        L"Content-Type: application/json\r\n"
        L"Accept: application/json\r\n";

    LPVOID data = nullptr;

    if (!body.empty())
    {
        data =
            const_cast<char*>(
                body.data()
                );
    }

    BOOL ok =
        WinHttpSendRequest(
            request,
            headers,
            static_cast<DWORD>(-1),
            data,
            static_cast<DWORD>(
                body.size()
                ),
            static_cast<DWORD>(
                body.size()
                ),
            0
        );

    if (ok)
    {
        ok =
            WinHttpReceiveResponse(
                request,
                nullptr
            );
    }

    if (ok)
    {
        DWORD statusLength =
            sizeof(status);

        WinHttpQueryHeaders(
            request,
            WINHTTP_QUERY_STATUS_CODE |
            WINHTTP_QUERY_FLAG_NUMBER,
            WINHTTP_HEADER_NAME_BY_INDEX,
            &status,
            &statusLength,
            WINHTTP_NO_HEADER_INDEX
        );
    }

    if (ok)
    {
        std::vector<char> buffer(
            32 * 1024
        );

        while (true)
        {
            if (g_stop.load())
            {
                ok = FALSE;
                break;
            }

            DWORD received = 0;

            if (!WinHttpReadData(
                request,
                buffer.data(),
                static_cast<DWORD>(
                    buffer.size()
                    ),
                &received))
            {
                ok = FALSE;
                break;
            }

            if (received == 0)
                break;

            response.append(
                buffer.data(),
                received
            );
        }
    }

    WinHttpCloseHandle(request);
    WinHttpCloseHandle(connection);
    WinHttpCloseHandle(session);

    return ok == TRUE;
}

// ============================================================
// FIREBASE LOGIN
// ============================================================

static bool FirebaseLogin(
    const std::wstring& username,
    const std::wstring& password,
    std::wstring& error)
{
    const std::wstring url =
        std::wstring(
            FIREBASE_SIGNIN_URL
        ) +
        FIREBASE_API_KEY;

    const std::string emailUtf8 =
        Utf8(username);

    const std::string passwordUtf8 =
        Utf8(password);

    const std::string body =
        "{\"email\":\"" +
        JsonEscape(emailUtf8) +
        "\",\"password\":\"" +
        JsonEscape(passwordUtf8) +
        "\",\"returnSecureToken\":true}";

    std::string response;
    DWORD status = 0;

    if (!HttpRequest(
        url,
        L"POST",
        body,
        response,
        status))
    {
        error =
            L"AUTH_REQUEST_FAILED";

        return false;
    }

    if (status < 200 ||
        status >= 300)
    {
        const std::string message =
            JsonString(
                response,
                "message"
            );

        error =
            Wide(
                message.empty()
                ? "AUTH_REQUEST_FAILED"
                : message
            );

        return false;
    }

    const std::string token =
        JsonString(
            response,
            "idToken"
        );

    const std::string uid =
        JsonString(
            response,
            "localId"
        );

    if (token.empty() ||
        uid.empty())
    {
        error =
            L"AUTH_RESPONSE_INVALID";

        return false;
    }

    g_firebaseToken =
        Wide(token);

    g_uid =
        Wide(uid);

    const std::wstring databaseUrl =
        std::wstring(
            FIREBASE_DB_URL
        ) +
        L"/users/" +
        g_uid +
        L".json?auth=" +
        g_firebaseToken;

    response.clear();
    status = 0;

    if (!HttpRequest(
        databaseUrl,
        L"GET",
        {},
        response,
        status))
    {
        error =
            L"ACCOUNT_PROFILE_REQUEST_FAILED";

        return false;
    }

    if (status < 200 ||
        status >= 300 ||
        response == "null")
    {
        error =
            L"ACCOUNT_PROFILE_NOT_FOUND";

        return false;
    }

    const bool active =
        JsonBool(
            response,
            "active",
            false
        );

    const std::string expires =
        JsonString(
            response,
            "licenseExpiresAt"
        );

    const std::string created =
        JsonString(
            response,
            "createdAt"
        );

    if (!active)
    {
        error =
            L"ACCOUNT_INACTIVE";

        return false;
    }

    if (expires.empty())
    {
        error =
            L"LICENSE_REQUIRED";

        return false;
    }

    if (!LicenseDateValid(expires))
    {
        error =
            L"LICENSE_EXPIRED";

        return false;
    }

    g_licenseExpires =
        Wide(expires);

    g_createdAt =
        Wide(created);

    return true;
}

// ============================================================
// DOWNLOAD
// ============================================================

static bool DownloadUrl(
    const std::wstring& url,
    const fs::path& output,
    int progressBase,
    int progressSpan)
{
    URL_COMPONENTS components{};

    components.dwStructSize =
        sizeof(components);

    wchar_t host[512]{};
    wchar_t path[8192]{};
    wchar_t extra[8192]{};

    components.lpszHostName = host;
    components.dwHostNameLength = 511;

    components.lpszUrlPath = path;
    components.dwUrlPathLength = 8191;

    components.lpszExtraInfo = extra;
    components.dwExtraInfoLength = 8191;

    if (!WinHttpCrackUrl(
        url.c_str(),
        0,
        0,
        &components))
    {
        return false;
    }

    HINTERNET session =
        WinHttpOpen(
            L"ZarexLoader/2.0",
            WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY,
            WINHTTP_NO_PROXY_NAME,
            WINHTTP_NO_PROXY_BYPASS,
            0
        );

    if (!session)
        return false;

    WinHttpSetTimeouts(
        session,
        15000,
        15000,
        30000,
        30000
    );

    HINTERNET connection =
        WinHttpConnect(
            session,
            host,
            components.nPort,
            0
        );

    if (!connection)
    {
        WinHttpCloseHandle(session);
        return false;
    }

    const std::wstring object =
        std::wstring(path) +
        std::wstring(extra);

    DWORD flags = 0;

    if (components.nScheme ==
        INTERNET_SCHEME_HTTPS)
    {
        flags |= WINHTTP_FLAG_SECURE;
    }

    HINTERNET request =
        WinHttpOpenRequest(
            connection,
            L"GET",
            object.c_str(),
            nullptr,
            WINHTTP_NO_REFERER,
            WINHTTP_DEFAULT_ACCEPT_TYPES,
            flags
        );

    if (!request)
    {
        WinHttpCloseHandle(connection);
        WinHttpCloseHandle(session);
        return false;
    }

    WinHttpSetTimeouts(
        request,
        15000,
        15000,
        30000,
        30000
    );

    BOOL ok =
        WinHttpSendRequest(
            request,
            WINHTTP_NO_ADDITIONAL_HEADERS,
            0,
            WINHTTP_NO_REQUEST_DATA,
            0,
            0,
            0
        );

    if (ok)
    {
        ok =
            WinHttpReceiveResponse(
                request,
                nullptr
            );
    }

    DWORD status = 0;
    DWORD statusLength =
        sizeof(status);

    if (ok)
    {
        WinHttpQueryHeaders(
            request,
            WINHTTP_QUERY_STATUS_CODE |
            WINHTTP_QUERY_FLAG_NUMBER,
            WINHTTP_HEADER_NAME_BY_INDEX,
            &status,
            &statusLength,
            WINHTTP_NO_HEADER_INDEX
        );
    }

    if (!ok ||
        status < 200 ||
        status >= 300)
    {
        ok = FALSE;
    }

    if (ok)
    {
        std::error_code ec;

        fs::create_directories(
            output.parent_path(),
            ec
        );

        std::ofstream file(
            output,
            std::ios::binary |
            std::ios::trunc
        );

        if (!file)
        {
            ok = FALSE;
        }
        else
        {
            DWORD total32 = 0;
            DWORD totalLength =
                sizeof(total32);

            WinHttpQueryHeaders(
                request,
                WINHTTP_QUERY_CONTENT_LENGTH |
                WINHTTP_QUERY_FLAG_NUMBER,
                WINHTTP_HEADER_NAME_BY_INDEX,
                &total32,
                &totalLength,
                WINHTTP_NO_HEADER_INDEX
            );

            const ULONGLONG total =
                static_cast<ULONGLONG>(
                    total32
                    );

            ULONGLONG received = 0;

            std::vector<char> buffer(
                64 * 1024
            );

            while (ok)
            {
                if (g_stop.load())
                {
                    ok = FALSE;
                    break;
                }

                DWORD bytesRead = 0;

                if (!WinHttpReadData(
                    request,
                    buffer.data(),
                    static_cast<DWORD>(
                        buffer.size()
                        ),
                    &bytesRead))
                {
                    ok = FALSE;
                    break;
                }

                if (bytesRead == 0)
                    break;

                file.write(
                    buffer.data(),
                    bytesRead
                );

                if (!file)
                {
                    ok = FALSE;
                    break;
                }

                received += bytesRead;

                if (total > 0)
                {
                    int progress =
                        progressBase +
                        static_cast<int>(
                            (
                                received *
                                static_cast<ULONGLONG>(
                                    progressSpan
                                    )
                                ) / total
                            );

                    progress =
                        std::clamp(
                            progress,
                            progressBase,
                            progressBase +
                            progressSpan
                        );

                    g_progress =
                        progress;

                    if (g_hwnd)
                    {
                        PostMessageW(
                            g_hwnd,
                            WM_APP_STATUS,
                            1,
                            0
                        );
                    }
                }
            }

            if (total > 0 && ok)
            {
                g_progress =
                    progressBase +
                    progressSpan;
            }

            file.close();
        }
    }

    WinHttpCloseHandle(request);
    WinHttpCloseHandle(connection);
    WinHttpCloseHandle(session);

    if (!ok)
    {
        std::error_code ec;

        fs::remove(
            output,
            ec
        );
    }

    return ok == TRUE;
}

// ============================================================
// PROCESS
// ============================================================

static bool RunProcess(
    const std::wstring& executable,
    const std::wstring& arguments,
    const fs::path& workingDirectory,
    DWORD waitMs)
{
    std::wstring commandLine =
        L"\"" +
        executable +
        L"\" " +
        arguments;

    std::vector<wchar_t> mutableCommand(
        commandLine.begin(),
        commandLine.end()
    );

    mutableCommand.push_back(
        L'\0'
    );

    STARTUPINFOW startup{};

    startup.cb =
        sizeof(startup);

    PROCESS_INFORMATION process{};

    const std::wstring workingDir =
        workingDirectory.empty()
        ? L""
        : workingDirectory.wstring();

    BOOL created =
        CreateProcessW(
            executable.c_str(),
            mutableCommand.data(),
            nullptr,
            nullptr,
            FALSE,
            CREATE_NO_WINDOW,
            nullptr,
            workingDir.empty()
            ? nullptr
            : workingDir.c_str(),
            &startup,
            &process
        );

    if (!created)
        return false;

    const DWORD waitResult =
        WaitForSingleObject(
            process.hProcess,
            waitMs
        );

    if (waitResult != WAIT_OBJECT_0)
    {
        CloseHandle(
            process.hThread
        );

        CloseHandle(
            process.hProcess
        );

        return false;
    }

    DWORD exitCode = 1;

    if (!GetExitCodeProcess(
        process.hProcess,
        &exitCode))
    {
        exitCode = 1;
    }

    CloseHandle(
        process.hThread
    );

    CloseHandle(
        process.hProcess
    );

    return exitCode == 0;
}

// ============================================================
// JAVA
// ============================================================

static fs::path FindJava()
{
    wchar_t javaHome[32768]{};

    const DWORD length =
        GetEnvironmentVariableW(
            L"JAVA_HOME",
            javaHome,
            static_cast<DWORD>(
                std::size(javaHome)
                )
        );

    if (length &&
        length < std::size(javaHome))
    {
        fs::path javaPath =
            fs::path(javaHome) /
            L"bin" /
            L"javaw.exe";

        if (fs::is_regular_file(
            javaPath))
        {
            return javaPath;
        }

        javaPath =
            fs::path(javaHome) /
            L"bin" /
            L"java.exe";

        if (fs::is_regular_file(
            javaPath))
        {
            return javaPath;
        }
    }

    wchar_t pathEnvironment[32768]{};

    const DWORD pathLength =
        GetEnvironmentVariableW(
            L"PATH",
            pathEnvironment,
            static_cast<DWORD>(
                std::size(pathEnvironment)
                )
        );

    if (!pathLength)
        return {};

    std::wstring path(
        pathEnvironment,
        pathLength
    );

    size_t position = 0;

    while (position <= path.size())
    {
        const size_t separator =
            path.find(
                L';',
                position
            );

        const std::wstring directory =
            path.substr(
                position,
                separator ==
                std::wstring::npos
                ? std::wstring::npos
                : separator - position
            );

        if (!directory.empty())
        {
            fs::path javaPath =
                fs::path(directory) /
                L"javaw.exe";

            if (fs::is_regular_file(
                javaPath))
            {
                return javaPath;
            }

            javaPath =
                fs::path(directory) /
                L"java.exe";

            if (fs::is_regular_file(
                javaPath))
            {
                return javaPath;
            }
        }

        if (separator ==
            std::wstring::npos)
        {
            break;
        }

        position =
            separator + 1;
    }

    return {};
}

// ============================================================
// MINECRAFT LAUNCHER
// ============================================================

static fs::path FindMinecraftLauncher()
{
    std::vector<fs::path> candidates;

    wchar_t programFiles[32768]{};
    wchar_t programFilesX86[32768]{};

    const DWORD pf =
        GetEnvironmentVariableW(
            L"ProgramFiles",
            programFiles,
            static_cast<DWORD>(
                std::size(programFiles)
                )
        );

    const DWORD pf86 =
        GetEnvironmentVariableW(
            L"ProgramFiles(x86)",
            programFilesX86,
            static_cast<DWORD>(
                std::size(programFilesX86)
                )
        );

    if (pf)
    {
        candidates.push_back(
            fs::path(programFiles) /
            L"Minecraft Launcher" /
            L"MinecraftLauncher.exe"
        );
    }

    if (pf86)
    {
        candidates.push_back(
            fs::path(programFilesX86) /
            L"Minecraft Launcher" /
            L"MinecraftLauncher.exe"
        );
    }

    candidates.push_back(
        L"C:\\XboxGames\\Minecraft Launcher\\"
        L"Content\\Minecraft.exe"
    );

    for (const auto& candidate :
        candidates)
    {
        if (fs::is_regular_file(
            candidate))
        {
            return candidate;
        }
    }

    return {};
}

// ============================================================
// FABRIC
// ============================================================

static bool InstallFabric(
    const fs::path& gameDirectory,
    const fs::path& installer)
{
    const fs::path java =
        FindJava();

    if (java.empty())
        return false;

    std::wstringstream arguments;

    arguments
        << L"-jar \""
        << installer.wstring()
        << L"\" client"
        << L" -dir \""
        << gameDirectory.wstring()
        << L"\""
        << L" -mcversion "
        << MC_VERSION
        << L" -loader "
        << FABRIC_LOADER;

    return RunProcess(
        java.wstring(),
        arguments.str(),
        gameDirectory,
        INFINITE
    );
}

// ============================================================
// FILES
// ============================================================

static void CopyOptions(
    const fs::path& gameDirectory)
{
    std::error_code ec;

    const fs::path publicDirectory =
        PublicZarex();

    fs::create_directories(
        publicDirectory,
        ec
    );

    const fs::path source =
        publicDirectory /
        L"options.txt";

    const fs::path destination =
        gameDirectory /
        L"options.txt";

    if (fs::is_regular_file(
        source))
    {
        fs::copy_file(
            source,
            destination,
            fs::copy_options::overwrite_existing,
            ec
        );
    }
}

// ============================================================
// LAUNCHER
// ============================================================

static void LaunchOfficialLauncher()
{
    const fs::path launcher =
        FindMinecraftLauncher();

    if (launcher.empty())
    {
        MessageBoxW(
            g_hwnd,
            L"Nie znaleziono oficjalnego "
            L"Minecraft Launcher.exe.",
            APP_NAME,
            MB_ICONERROR
        );

        return;
    }

    HINSTANCE result =
        ShellExecuteW(
            nullptr,
            L"open",
            launcher.c_str(),
            nullptr,
            launcher.parent_path().c_str(),
            SW_SHOWNORMAL
        );

    if (reinterpret_cast<INT_PTR>(
        result) <= 32)
    {
        MessageBoxW(
            g_hwnd,
            L"Nie udało się uruchomić "
            L"Minecraft Launchera.",
            APP_NAME,
            MB_ICONERROR
        );
    }
}

// ============================================================
// LOGIN WORKER
// ============================================================

static void LoginWorker(
    std::wstring username,
    std::wstring password)
{
    g_working = true;

    PostStatus(
        L"Logging in..."
    );

    std::wstring error;

    const bool success =
        FirebaseLogin(
            username,
            password,
            error
        );

    if (g_stop.load())
        return;

    PostOperationResult(
        WM_APP_LOGIN_FINISHED,
        success,
        error
    );
}

// ============================================================
// MAIN WORKER
// ============================================================

static void Worker()
{
    g_working = true;
    g_progress = 0;

    const fs::path gameDirectory =
        TempGame();

    std::error_code ec;

    fs::create_directories(
        gameDirectory,
        ec
    );

    if (ec)
    {
        PostStatus(
            L"Cannot create game directory"
        );

        PostOperationResult(
            WM_APP_LOAD_FINISHED,
            false,
            L"Nie można utworzyć katalogu gry."
        );

        return;
    }

    // --------------------------------------------------------
    // Fabric installer
    // --------------------------------------------------------

    PostStatus(
        L"Downloading Fabric..."
    );

    const fs::path installer =
        gameDirectory.parent_path() /
        L"fabric-installer-1.0.3.jar";

    if (!DownloadUrl(
        FABRIC_INSTALLER_URL,
        installer,
        0,
        15))
    {
        PostOperationResult(
            WM_APP_LOAD_FINISHED,
            false,
            L"Fabric download failed."
        );

        return;
    }

    if (g_stop.load())
        return;

    // --------------------------------------------------------
    // Fabric installation
    // --------------------------------------------------------

    PostStatus(
        L"Installing Fabric..."
    );

    if (!InstallFabric(
        gameDirectory,
        installer))
    {
        PostOperationResult(
            WM_APP_LOAD_FINISHED,
            false,
            L"Fabric installation failed."
        );

        return;
    }

    if (g_stop.load())
        return;

    // --------------------------------------------------------
    // Mods directory
    // --------------------------------------------------------

    const fs::path mods =
        gameDirectory / L"mods";

    fs::create_directories(
        mods,
        ec
    );

    if (ec)
    {
        PostOperationResult(
            WM_APP_LOAD_FINISHED,
            false,
            L"Cannot create mods directory."
        );

        return;
    }

    // --------------------------------------------------------
    // Fabric API
    // --------------------------------------------------------

    PostStatus(
        L"Downloading Fabric API..."
    );

    if (!DownloadUrl(
        FABRIC_API_URL,
        mods /
        L"fabric-api-0.141.6+1.21.11.jar",
        15,
        35))
    {
        PostOperationResult(
            WM_APP_LOAD_FINISHED,
            false,
            L"Fabric API download failed."
        );

        return;
    }

    if (g_stop.load())
        return;

    // --------------------------------------------------------
    // Zarex
    // --------------------------------------------------------

    PostStatus(
        L"Downloading Zarex Client..."
    );

    if (!DownloadUrl(
        ZAREX_MOD_URL,
        mods /
        L"zarex-client-1.0.0.jar",
        50,
        40))
    {
        PostOperationResult(
            WM_APP_LOAD_FINISHED,
            false,
            L"Zarex download failed."
        );

        return;
    }

    if (g_stop.load())
        return;

    // --------------------------------------------------------
    // Options
    // --------------------------------------------------------

    CopyOptions(
        gameDirectory
    );

    g_progress = 100;

    PostStatus(
        L"Starting Minecraft..."
    );

    Sleep(450);

    if (g_stop.load())
        return;

    // Inform UI thread that the work is complete.
    PostOperationResult(
        WM_APP_LOAD_FINISHED,
        true
    );
}

// ============================================================
// DRAWING - TEXT
// ============================================================

static void Text(
    Graphics& graphics,
    const std::wstring& text,
    Real x,
    Real y,
    Real width,
    Real height,
    Real size,
    Color color,
    Gdiplus::StringAlignment alignment =
    Gdiplus::StringAlignmentNear,
    const wchar_t* family =
    L"Segoe UI Light",
    Gdiplus::FontStyle style =
    Gdiplus::FontStyleRegular)
{
    if (text.empty())
        return;

    FontFamily fontFamily(family);

    Font font(
        &fontFamily,
        size,
        style,
        Gdiplus::UnitPixel
    );

    SolidBrush brush(color);

    Gdiplus::StringFormat format;

    format.SetAlignment(alignment);
    format.SetLineAlignment(
        Gdiplus::StringAlignmentCenter
    );
    format.SetTrimming(
        Gdiplus::StringTrimmingEllipsisCharacter
    );
    format.SetFormatFlags(
        Gdiplus::StringFormatFlagsNoWrap
    );

    RectF rectangle(
        x,
        y,
        width,
        height
    );

    graphics.DrawString(
        text.c_str(),
        -1,
        &font,
        rectangle,
        &format,
        &brush
    );
}

// ============================================================
// ROUNDED RECT
// ============================================================

static void RoundedRect(
    Graphics& graphics,
    Real x,
    Real y,
    Real width,
    Real height,
    Real radius,
    Color fill)
{
    if (width <= 0.0f ||
        height <= 0.0f)
    {
        return;
    }

    radius =
        std::max(
            0.0f,
            std::min(
                radius,
                std::min(
                    width,
                    height
                ) / 2.0f
            )
        );

    const Real diameter =
        radius * 2.0f;

    GraphicsPath path;

    path.AddArc(
        x,
        y,
        diameter,
        diameter,
        180.0f,
        90.0f
    );

    path.AddArc(
        x + width - diameter,
        y,
        diameter,
        diameter,
        270.0f,
        90.0f
    );

    path.AddArc(
        x + width - diameter,
        y + height - diameter,
        diameter,
        diameter,
        0.0f,
        90.0f
    );

    path.AddArc(
        x,
        y + height - diameter,
        diameter,
        diameter,
        90.0f,
        90.0f
    );

    path.CloseFigure();

    SolidBrush brush(
        fill
    );

    graphics.FillPath(
        &brush,
        &path
    );
}

// ============================================================
// BORDER GLOW
// ============================================================

static void BorderGlow(
    Graphics& graphics,
    Real x,
    Real y,
    Real width,
    Real height,
    Real radius)
{
    const Real diameter =
        radius * 2.0f;

    for (int i = 10;
        i >= 1;
        --i)
    {
        const BYTE alpha =
            static_cast<BYTE>(
                3 + (10 - i) * 2
                );

        Pen pen(
            C(
                alpha,
                255,
                255,
                255
            ),
            static_cast<Real>(i)
        );

        GraphicsPath path;

        path.AddArc(
            x,
            y,
            diameter,
            diameter,
            180.0f,
            90.0f
        );

        path.AddArc(
            x + width - diameter,
            y,
            diameter,
            diameter,
            270.0f,
            90.0f
        );

        path.AddArc(
            x + width - diameter,
            y + height - diameter,
            diameter,
            diameter,
            0.0f,
            90.0f
        );

        path.AddArc(
            x,
            y + height - diameter,
            diameter,
            diameter,
            90.0f,
            90.0f
        );

        path.CloseFigure();

        graphics.DrawPath(
            &pen,
            &path
        );
    }
}

// ============================================================
// LOGO
// ============================================================

static void DrawLogo(
    Graphics& graphics,
    Real x,
    Real y,
    Real size)
{
    Text(
        graphics,
        L"za",
        x,
        y,
        size * 0.95f,
        size * 0.75f,
        size,
        C(245, 245, 245),
        Gdiplus::StringAlignmentNear,
        L"Segoe UI Light"
    );

    Text(
        graphics,
        L"rex",
        x + size * 0.62f,
        y,
        size * 1.15f,
        size * 0.75f,
        size,
        C(245, 235, 55),
        Gdiplus::StringAlignmentNear,
        L"Segoe UI Light"
    );
}

// ============================================================
// BACKGROUND
// ============================================================

static void DrawBackground(
    Graphics& graphics,
    int width,
    int height)
{
    LinearGradientBrush background(
        RectF(
            0.0f,
            0.0f,
            static_cast<Real>(width),
            static_cast<Real>(height)
        ),
        C(255, 12, 12, 14),
        C(255, 25, 25, 29),
        Gdiplus::LinearGradientModeVertical
    );

    graphics.FillRectangle(
        &background,
        0.0f,
        0.0f,
        static_cast<Real>(width),
        static_cast<Real>(height)
    );

    for (int i = 18;
        i >= 1;
        --i)
    {
        const BYTE alpha =
            static_cast<BYTE>(
                2 + (18 - i)
                );

        SolidBrush glow(
            C(
                alpha,
                130,
                130,
                130
            )
        );

        graphics.FillRectangle(
            &glow,
            static_cast<Real>(
                18 - i
                ),
            static_cast<Real>(
                18 - i
                ),
            static_cast<Real>(
                width - 36 + i * 2
                ),
            static_cast<Real>(
                height - 36 + i * 2
                )
        );
    }
}

// ============================================================
// LOGIN
// ============================================================

static void DrawLogin(
    Graphics& graphics,
    int width,
    int height)
{
    DrawBackground(
        graphics,
        width,
        height
    );

    BorderGlow(
        graphics,
        60.0f,
        65.0f,
        static_cast<Real>(
            width - 120
            ),
        static_cast<Real>(
            height - 100
            ),
        32.0f
    );

    RoundedRect(
        graphics,
        60.0f,
        65.0f,
        static_cast<Real>(
            width - 120
            ),
        static_cast<Real>(
            height - 100
            ),
        32.0f,
        C(36, 36, 36)
    );

    DrawLogo(
        graphics,
        257.0f,
        197.0f,
        38.0f
    );

    Text(
        graphics,
        L"username",
        190.0f,
        269.0f,
        275.0f,
        22.0f,
        14.0f,
        C(210, 210, 210)
    );

    Text(
        graphics,
        L"password",
        190.0f,
        339.0f,
        275.0f,
        22.0f,
        14.0f,
        C(210, 210, 210)
    );

    for (int i = 0;
        i < 16;
        ++i)
    {
        const BYTE red =
            static_cast<BYTE>(
                53 - i * 2
                );

        const BYTE green =
            static_cast<BYTE>(
                52 - i
                );

        const BYTE blue =
            static_cast<BYTE>(
                53 - i
                );

        RoundedRect(
            graphics,
            183.0f +
            static_cast<Real>(i) *
            0.3f,
            292.0f +
            static_cast<Real>(i) *
            0.1f,
            274.0f -
            static_cast<Real>(i) *
            0.6f,
            29.0f -
            static_cast<Real>(i) *
            0.2f,
            4.0f,
            C(
                red,
                green,
                blue
            )
        );
    }

    for (int i = 0;
        i < 16;
        ++i)
    {
        const BYTE red =
            static_cast<BYTE>(
                53 - i * 2
                );

        const BYTE green =
            static_cast<BYTE>(
                52 - i
                );

        const BYTE blue =
            static_cast<BYTE>(
                53 - i
                );

        RoundedRect(
            graphics,
            183.0f +
            static_cast<Real>(i) *
            0.3f,
            362.0f +
            static_cast<Real>(i) *
            0.1f,
            274.0f -
            static_cast<Real>(i) *
            0.6f,
            29.0f -
            static_cast<Real>(i) *
            0.2f,
            4.0f,
            C(
                red,
                green,
                blue
            )
        );
    }

    if (g_hoverTarget == UIHoverTarget::Username || g_userFocus)
    {
        BorderGlow(
            graphics,
            183.0f,
            292.0f,
            274.0f,
            29.0f,
            4.0f
        );
    }

    if (g_hoverTarget == UIHoverTarget::Password || g_passFocus)
    {
        BorderGlow(
            graphics,
            183.0f,
            362.0f,
            274.0f,
            29.0f,
            4.0f
        );
    }

    LinearGradientBrush userGradient(
        RectF(
            300.0f,
            292.0f,
            158.0f,
            29.0f
        ),
        C(30, 255, 236, 30),
        C(110, 255, 235, 30),
        Gdiplus::LinearGradientModeHorizontal
    );

    graphics.FillRectangle(
        &userGradient,
        300.0f,
        292.0f,
        158.0f,
        29.0f
    );

    LinearGradientBrush passGradient(
        RectF(
            300.0f,
            362.0f,
            158.0f,
            29.0f
        ),
        C(30, 255, 236, 30),
        C(110, 255, 235, 30),
        Gdiplus::LinearGradientModeHorizontal
    );

    graphics.FillRectangle(
        &passGradient,
        300.0f,
        362.0f,
        158.0f,
        29.0f
    );

    if (!g_user.empty())
    {
        Text(
            graphics,
            g_user,
            191.0f,
            293.0f,
            260.0f,
            27.0f,
            13.0f,
            C(225, 225, 225)
        );
    }

    if (!g_pass.empty())
    {
        Text(
            graphics,
            std::wstring(
                g_pass.size(),
                L'\x2022'
            ),
            191.0f,
            363.0f,
            260.0f,
            27.0f,
            13.0f,
            C(225, 225, 225)
        );
    }

    if (g_userFocus)
    {
        Text(
            graphics,
            L"|",
            191.0f +
            static_cast<Real>(
                g_user.size()
                ) * 7.1f,
            293.0f,
            10.0f,
            27.0f,
            13.0f,
            C(245, 245, 245)
        );
    }

    if (g_passFocus)
    {
        Text(
            graphics,
            L"|",
            191.0f +
            static_cast<Real>(
                g_pass.size()
                ) * 7.1f,
            363.0f,
            10.0f,
            27.0f,
            13.0f,
            C(245, 245, 245)
        );
    }

    const bool loginHovered =
        g_hoverTarget == UIHoverTarget::Login;
    const bool loginPressed =
        g_pressedTarget == UIHoverTarget::Login;

    const Real loginY =
        405.0f + (loginPressed ? 1.0f : 0.0f);

    RoundedRect(
        graphics,
        245.0f,
        loginY,
        140.0f,
        41.0f,
        9.0f,
        loginHovered
            ? C(255, 255, 240)
            : C(245, 225, 45)
    );

    if (loginHovered)
    {
        BorderGlow(
            graphics,
            245.0f,
            loginY,
            140.0f,
            41.0f,
            9.0f
        );
    }

    Text(
        graphics,
        L"Login",
        245.0f,
        loginY,
        140.0f,
        41.0f,
        13.0f,
        C(20, 20, 20),
        Gdiplus::StringAlignmentCenter,
        L"Segoe UI",
        Gdiplus::FontStyleBold
    );

    Text(
        graphics,
        L"Save Credentials",
        263.0f,
        445.0f,
        90.0f,
        20.0f,
        10.0f,
        C(205, 205, 205),
        Gdiplus::StringAlignmentCenter
    );

    RoundedRect(
        graphics,
        356.0f,
        446.0f,
        17.0f,
        17.0f,
        4.0f,
        C(62, 62, 62)
    );

    if (g_save)
    {
        Text(
            graphics,
            L"\x2713",
            356.0f,
            444.0f,
            17.0f,
            19.0f,
            13.0f,
            C(230, 230, 230),
            Gdiplus::StringAlignmentCenter
        );
    }
}

// ============================================================
// AVATAR
// ============================================================

static void DrawAvatar(
    Graphics& graphics,
    Real centerX,
    Real centerY)
{
    SolidBrush yellow(
        C(255, 255, 250, 60)
    );

    graphics.FillEllipse(
        &yellow,
        centerX - 10.0f,
        centerY - 19.0f,
        20.0f,
        20.0f
    );

    GraphicsPath path;

    path.AddArc(
        centerX - 18.0f,
        centerY - 1.0f,
        36.0f,
        30.0f,
        180.0f,
        180.0f
    );

    path.CloseFigure();

    graphics.FillPath(
        &yellow,
        &path
    );
}

// ============================================================
// ZC ICON
// ============================================================

static void DrawZCIcon(
    Graphics& graphics,
    Real x,
    Real y)
{
    RoundedRect(
        graphics,
        x,
        y,
        38.0f,
        38.0f,
        7.0f,
        C(10, 10, 10)
    );

    Pen yellow(
        C(245, 225, 0),
        2.0f
    );

    graphics.DrawEllipse(
        &yellow,
        x + 5.0f,
        y + 5.0f,
        28.0f,
        28.0f
    );

    Text(
        graphics,
        L"ZC",
        x + 5.0f,
        y + 5.0f,
        28.0f,
        28.0f,
        11.0f,
        C(245, 225, 0),
        Gdiplus::StringAlignmentCenter,
        L"Segoe UI",
        Gdiplus::FontStyleBold
    );
}

// ============================================================
// MENU
// ============================================================

static void DrawMenu(
    Graphics& graphics,
    int width,
    int height)
{
    DrawBackground(
        graphics,
        width,
        height
    );

    BorderGlow(
        graphics,
        38.0f,
        40.0f,
        static_cast<Real>(
            width - 76
            ),
        static_cast<Real>(
            height - 74
            ),
        32.0f
    );

    RoundedRect(
        graphics,
        38.0f,
        40.0f,
        static_cast<Real>(
            width - 76
            ),
        static_cast<Real>(
            height - 74
            ),
        32.0f,
        C(36, 36, 36)
    );

    DrawLogo(
        graphics,
        80.0f,
        83.0f,
        37.0f
    );

    Text(
        graphics,
        L"Information",
        72.0f,
        143.0f,
        200.0f,
        22.0f,
        12.0f,
        C(205, 205, 205)
    );

    RoundedRect(
        graphics,
        72.0f,
        164.0f,
        365.0f,
        112.0f,
        13.0f,
        C(43, 43, 43)
    );

    DrawAvatar(
        graphics,
        132.0f,
        220.0f
    );

    Text(
        graphics,
        g_user.empty()
        ? L"user"
        : g_user,
        194.0f,
        190.0f,
        180.0f,
        22.0f,
        12.0f,
        C(225, 225, 225)
    );

    Text(
        graphics,
        L"Created at: " +
        (
            g_createdAt.empty()
            ? L"-"
            : g_createdAt
            ),
        194.0f,
        211.0f,
        220.0f,
        18.0f,
        10.0f,
        C(115, 115, 115)
    );

    Text(
        graphics,
        L"License expires: " +
        (
            g_licenseExpires.empty()
            ? L"-"
            : g_licenseExpires
            ),
        194.0f,
        228.0f,
        220.0f,
        18.0f,
        10.0f,
        C(115, 115, 115)
    );

    Text(
        graphics,
        L"License: ACTIVE",
        194.0f,
        245.0f,
        180.0f,
        18.0f,
        11.0f,
        C(170, 210, 80)
    );

    Text(
        graphics,
        L"Version",
        72.0f,
        287.0f,
        200.0f,
        22.0f,
        12.0f,
        C(205, 205, 205)
    );

    RoundedRect(
        graphics,
        72.0f,
        306.0f,
        250.0f,
        111.0f,
        13.0f,
        C(43, 43, 43)
    );

    DrawZCIcon(
        graphics,
        87.0f,
        320.0f
    );

    Text(
        graphics,
        L"Zarex Stable",
        134.0f,
        318.0f,
        150.0f,
        20.0f,
        12.0f,
        C(220, 220, 220)
    );

    Text(
        graphics,
        L"Last Update: 22/11/2022",
        134.0f,
        337.0f,
        180.0f,
        20.0f,
        11.0f,
        C(112, 112, 112)
    );

    DrawZCIcon(
        graphics,
        87.0f,
        368.0f
    );

    Text(
        graphics,
        L"Zarex Testing",
        134.0f,
        366.0f,
        150.0f,
        20.0f,
        12.0f,
        C(220, 220, 220)
    );

    Text(
        graphics,
        L"Last Update: 25/12/2022",
        134.0f,
        385.0f,
        180.0f,
        20.0f,
        11.0f,
        C(112, 112, 112)
    );

    RoundedRect(
        graphics,
        79.0f,
        427.0f,
        125.0f,
        29.0f,
        4.0f,
        C(57, 56, 57)
    );

    LinearGradientBrush loadGradient(
        RectF(
            79.0f,
            427.0f,
            125.0f,
            29.0f
        ),
        C(100, 94, 35),
        C(55, 54, 55),
        Gdiplus::LinearGradientModeHorizontal
    );

    graphics.FillRectangle(
        &loadGradient,
        79.0f,
        427.0f,
        125.0f,
        29.0f
    );

    Text(
        graphics,
        L"Load",
        79.0f,
        427.0f,
        125.0f,
        29.0f,
        13.0f,
        C(225, 225, 225),
        Gdiplus::StringAlignmentCenter
    );

    Text(
        graphics,
        L"Last Updates:",
        472.0f,
        80.0f,
        160.0f,
        22.0f,
        11.0f,
        C(205, 205, 205)
    );

    RoundedRect(
        graphics,
        464.0f,
        94.0f,
        216.0f,
        355.0f,
        13.0f,
        C(43, 43, 43)
    );
}

// ============================================================
// SPINNER
// ============================================================

static void DrawSpinner(
    Graphics& graphics,
    Real centerX,
    Real centerY)
{
    constexpr int segments = 18;

    const int active =
        g_spinner.load() %
        segments;

    constexpr Real pi =
        3.14159265358979323846f;

    for (int i = 0;
        i < segments;
        ++i)
    {
        const Real angle =
            static_cast<Real>(
                i * 360.0 /
                segments
                );

        const Real radians =
            angle * pi / 180.0f;

        const Real x1 =
            centerX +
            std::cos(radians) *
            12.0f;

        const Real y1 =
            centerY +
            std::sin(radians) *
            12.0f;

        const Real x2 =
            centerX +
            std::cos(radians) *
            15.0f;

        const Real y2 =
            centerY +
            std::sin(radians) *
            15.0f;

        const BYTE alpha =
            static_cast<BYTE>(
                35 +
                (
                    (i - active +
                        segments) %
                    segments
                    ) * 11
                );

        Pen pen(
            C(
                alpha,
                250,
                235,
                40
            ),
            2.0f
        );

        graphics.DrawLine(
            &pen,
            x1,
            y1,
            x2,
            y2
        );
    }
}

// ============================================================
// LOADING
// ============================================================

static void DrawLoading(
    Graphics& graphics,
    int width,
    int height)
{
    DrawBackground(
        graphics,
        width,
        height
    );

    BorderGlow(
        graphics,
        30.0f,
        52.0f,
        static_cast<Real>(
            width - 70
            ),
        static_cast<Real>(
            height - 120
            ),
        34.0f
    );

    RoundedRect(
        graphics,
        30.0f,
        52.0f,
        static_cast<Real>(
            width - 70
            ),
        static_cast<Real>(
            height - 120
            ),
        34.0f,
        C(36, 36, 36)
    );

    DrawLogo(
        graphics,
        355.0f,
        143.0f,
        47.0f
    );

    Text(
        graphics,
        g_status,
        370.0f,
        214.0f,
        180.0f,
        28.0f,
        16.0f,
        C(215, 215, 215),
        Gdiplus::StringAlignmentCenter
    );

    DrawSpinner(
        graphics,
        487.0f,
        227.0f
    );

    RoundedRect(
        graphics,
        310.0f,
        260.0f,
        350.0f,
        6.0f,
        3.0f,
        C(55, 55, 55)
    );

    const int progress =
        std::clamp(
            g_progress.load(),
            0,
            100
        );

    if (progress > 0)
    {
        RoundedRect(
            graphics,
            310.0f,
            260.0f,
            350.0f *
            (
                static_cast<Real>(
                    progress
                    ) / 100.0f
                ),
            6.0f,
            3.0f,
            C(245, 225, 45)
        );
    }
}

// ============================================================
// DRAW SCREEN
// ============================================================

static void DrawScreen(
    HDC dc)
{
    RECT client{};
    GetClientRect(g_hwnd, &client);

    const int width = client.right - client.left;
    const int height = client.bottom - client.top;

    if (width <= 0 || height <= 0)
        return;

    if (!EnsureBackBuffer(dc, width, height))
        return;

    Graphics graphics(g_backDC);

    graphics.SetSmoothingMode(Gdiplus::SmoothingModeAntiAlias);
    graphics.SetPixelOffsetMode(Gdiplus::PixelOffsetModeHighQuality);
    graphics.SetCompositingQuality(Gdiplus::CompositingQualityHighQuality);
    graphics.SetInterpolationMode(Gdiplus::InterpolationModeHighQualityBicubic);
    graphics.SetTextRenderingHint(Gdiplus::TextRenderingHintAntiAliasGridFit);

    SolidBrush clearBrush(C(15, 15, 17));
    graphics.FillRectangle(
        &clearBrush,
        0.0f,
        0.0f,
        static_cast<Real>(width),
        static_cast<Real>(height)
    );

    if (g_screen == 0)
        DrawLogin(graphics, width, height);
    else if (g_screen == 1)
        DrawMenu(graphics, width, height);
    else
        DrawLoading(graphics, width, height);

    BitBlt(
        dc,
        0,
        0,
        width,
        height,
        g_backDC,
        0,
        0,
        SRCCOPY
    );
}

// ============================================================
// HIT TEST
// ============================================================

static bool InRect(
    int x,
    int y,
    int left,
    int top,
    int right,
    int bottom)
{
    return
        x >= left &&
        x <= right &&
        y >= top &&
        y <= bottom;
}

// ============================================================
// START LOGIN
// ============================================================

static void StartLogin()
{
    if (g_working.load())
        return;

    if (g_user.empty() ||
        g_pass.empty())
    {
        MessageBoxW(
            g_hwnd,
            L"Podaj username (email) i hasło.",
            APP_NAME,
            MB_ICONWARNING
        );

        return;
    }

    g_userFocus = false;
    g_passFocus = false;

    g_screen = 2;

    g_progress = 0;

    SetWindowClientSize(
        928,
        396
    );

    InvalidateRect(
        g_hwnd,
        nullptr,
        FALSE
    );

    if (g_worker.joinable())
    {
        g_worker.join();
    }

    g_stop = false;

    const std::wstring username =
        g_user;

    const std::wstring password =
        g_pass;

    g_worker =
        std::thread(
            LoginWorker,
            username,
            password
        );
}

// ============================================================
// START LOADING
// ============================================================

static void StartLoading()
{
    if (g_working.load())
        return;

    g_screen = 2;

    g_progress = 0;

    SetWindowClientSize(
        928,
        396
    );

    InvalidateRect(
        g_hwnd,
        nullptr,
        FALSE
    );

    if (g_worker.joinable())
    {
        g_worker.join();
    }

    g_stop = false;

    g_worker =
        std::thread(
            Worker
        );
}

// ============================================================
// WINDOW PROCEDURE
// ============================================================

static LRESULT CALLBACK WndProc(
    HWND hwnd,
    UINT message,
    WPARAM wParam,
    LPARAM lParam)
{
    switch (message)
    {
    case WM_CREATE:
    {
        SetTimer(
            hwnd,
            10,
            16,
            nullptr
        );

        return 0;
    }

    case WM_TIMER:
    {
        ++g_spinner;
        InvalidateRect(hwnd, nullptr, FALSE);
        return 0;
    }

    case WM_APP_STATUS:
    {
        if (wParam == 1)
        {
            InvalidateRect(
                hwnd,
                nullptr,
                FALSE
            );

            return 0;
        }

        auto* text =
            reinterpret_cast<
            std::wstring*
            >(lParam);

        if (text)
        {
            g_status =
                std::move(*text);

            delete text;

            InvalidateRect(
                hwnd,
                nullptr,
                FALSE
            );
        }

        return 0;
    }

    case WM_APP_LOGIN_FINISHED:
    {
        auto* result =
            reinterpret_cast<
            OperationResult*
            >(lParam);

        if (!result)
            return 0;

        const bool success =
            result->success;

        const std::wstring error =
            result->error;

        delete result;

        g_working = false;

        if (!success)
        {
            g_screen = 0;

            SetWindowClientSize(
                655,
                552
            );

            MessageBoxW(
                hwnd,
                (
                    L"Login failed:\n" +
                    error
                    ).c_str(),
                APP_NAME,
                MB_ICONERROR
            );
        }
        else
        {
            g_screen = 1;

            SetWindowClientSize(
                778,
                528
            );

            g_status =
                L"Logged in";
        }

        InvalidateRect(
            hwnd,
            nullptr,
            FALSE
        );

        return 0;
    }

    case WM_APP_LOAD_FINISHED:
    {
        auto* result =
            reinterpret_cast<
            OperationResult*
            >(lParam);

        if (!result)
            return 0;

        const bool success =
            result->success;

        const std::wstring error =
            result->error;

        delete result;

        g_working = false;

        if (!success)
        {
            g_screen = 1;

            SetWindowClientSize(
                778,
                528
            );

            MessageBoxW(
                hwnd,
                (
                    L"Nie udało się przygotować Minecrafta:\n" +
                    error
                    ).c_str(),
                APP_NAME,
                MB_ICONERROR
            );
        }
        else
        {
            g_status =
                L"Starting Minecraft...";

            InvalidateRect(
                hwnd,
                nullptr,
                FALSE
            );

            LaunchOfficialLauncher();
        }

        InvalidateRect(
            hwnd,
            nullptr,
            FALSE
        );

        return 0;
    }

    case WM_ERASEBKGND:
        return 1;

    case WM_PAINT:
    {
        PAINTSTRUCT paint{};

        HDC dc =
            BeginPaint(
                hwnd,
                &paint
            );

        DrawScreen(dc);

        EndPaint(
            hwnd,
            &paint
        );

        return 0;
    }

    case WM_NCHITTEST:
    {
        // Zostawiamy cały obszar jako CLIENT.
        // Dzięki temu przyciski i pola tekstowe
        // działają normalnie.
        return HTCLIENT;
    }

    case WM_MOUSEMOVE:
    {
        TRACKMOUSEEVENT tme{};
        tme.cbSize = sizeof(tme);
        tme.dwFlags = TME_LEAVE;
        tme.hwndTrack = hwnd;
        TrackMouseEvent(&tme);

        g_mouseX = GET_X_LPARAM(lParam);
        g_mouseY = GET_Y_LPARAM(lParam);

        UIHoverTarget target = UIHoverTarget::None;

        if (g_screen == 0)
        {
            if (InRect(g_mouseX, g_mouseY, 180, 285, 462, 326))
                target = UIHoverTarget::Username;
            else if (InRect(g_mouseX, g_mouseY, 180, 355, 462, 397))
                target = UIHoverTarget::Password;
            else if (InRect(g_mouseX, g_mouseY, 245, 405, 385, 446))
                target = UIHoverTarget::Login;
            else if (InRect(g_mouseX, g_mouseY, 350, 441, 380, 470))
                target = UIHoverTarget::Save;
        }
        else if (g_screen == 1)
        {
            if (InRect(g_mouseX, g_mouseY, 70, 420, 210, 465))
                target = UIHoverTarget::Load;
        }

        g_hoverTarget = target;
        return 0;
    }

    case WM_MOUSELEAVE:
    {
        g_hoverTarget = UIHoverTarget::None;
        return 0;
    }

    case WM_LBUTTONUP:
    {
        g_pressedTarget = UIHoverTarget::None;
        return 0;
    }

    case WM_LBUTTONDOWN:
    {
        const int x =
            GET_X_LPARAM(lParam);

        const int y =
            GET_Y_LPARAM(lParam);

        g_pressedTarget = UIHoverTarget::None;

        if (g_screen == 0)
        {
            const bool userArea =
                InRect(
                    x,
                    y,
                    180,
                    285,
                    462,
                    326
                );

            const bool passArea =
                InRect(
                    x,
                    y,
                    180,
                    355,
                    462,
                    397
                );

            const bool loginButton =
                InRect(
                    x,
                    y,
                    245,
                    405,
                    385,
                    446
                );

            const bool saveButton =
                InRect(
                    x,
                    y,
                    350,
                    441,
                    380,
                    470
                );

            if (userArea)
            {
                g_pressedTarget = UIHoverTarget::Username;
                g_userFocus = true;
                g_passFocus = false;
            }
            else if (passArea)
            {
                g_pressedTarget = UIHoverTarget::Password;
                g_userFocus = false;
                g_passFocus = true;
            }
            else if (loginButton)
            {
                g_pressedTarget = UIHoverTarget::Login;
                StartLogin();
            }
            else if (saveButton)
            {
                g_pressedTarget = UIHoverTarget::Save;
                g_save = !g_save;
            }
            else
            {
                // Drag window
                ReleaseCapture();

                SendMessageW(
                    hwnd,
                    WM_NCLBUTTONDOWN,
                    HTCAPTION,
                    0
                );
            }

            InvalidateRect(
                hwnd,
                nullptr,
                FALSE
            );

            return 0;
        }

        if (g_screen == 1)
        {
            const bool loadButton =
                InRect(
                    x,
                    y,
                    70,
                    420,
                    210,
                    465
                );

            if (loadButton)
            {
                g_pressedTarget = UIHoverTarget::Load;
                StartLoading();
            }
            else
            {
                ReleaseCapture();

                SendMessageW(
                    hwnd,
                    WM_NCLBUTTONDOWN,
                    HTCAPTION,
                    0
                );
            }

            return 0;
        }

        // Loading screen
        ReleaseCapture();

        SendMessageW(
            hwnd,
            WM_NCLBUTTONDOWN,
            HTCAPTION,
            0
        );

        return 0;
    }

    case WM_CHAR:
    {
        if (g_screen == 0)
        {
            const wchar_t character =
                static_cast<wchar_t>(
                    wParam
                    );

            if (character == L'\b')
            {
                if (g_userFocus &&
                    !g_user.empty())
                {
                    g_user.pop_back();
                }
                else if (g_passFocus &&
                    !g_pass.empty())
                {
                    g_pass.pop_back();
                }
            }
            else if (
                character >= 32 &&
                character != 127)
            {
                if (g_userFocus &&
                    g_user.size() < 128)
                {
                    g_user.push_back(
                        character
                    );
                }
                else if (g_passFocus &&
                    g_pass.size() < 128)
                {
                    g_pass.push_back(
                        character
                    );
                }
            }

            InvalidateRect(
                hwnd,
                nullptr,
                FALSE
            );
        }

        return 0;
    }

    case WM_KEYDOWN:
    {
        if (g_screen == 0)
        {
            if (wParam == VK_TAB)
            {
                const bool oldUser =
                    g_userFocus;

                g_userFocus =
                    !oldUser;

                g_passFocus =
                    oldUser;

                InvalidateRect(
                    hwnd,
                    nullptr,
                    FALSE
                );

                return 0;
            }

            if (wParam == VK_RETURN)
            {
                StartLogin();
                return 0;
            }

            if (wParam == VK_BACK)
            {
                if (g_userFocus &&
                    !g_user.empty())
                {
                    g_user.pop_back();
                }
                else if (g_passFocus &&
                    !g_pass.empty())
                {
                    g_pass.pop_back();
                }

                InvalidateRect(
                    hwnd,
                    nullptr,
                    FALSE
                );

                return 0;
            }
        }

        return 0;
    }

    case WM_SIZE:
    {
        RoundWindow();

        InvalidateRect(
            hwnd,
            nullptr,
            FALSE
        );

        return 0;
    }

    case WM_CLOSE:
    {
        if (g_working.load())
        {
            const int answer =
                MessageBoxW(
                    hwnd,
                    L"Trwa operacja. Czy na pewno chcesz zamknąć program?",
                    APP_NAME,
                    MB_YESNO |
                    MB_ICONQUESTION
                );

            if (answer != IDYES)
                return 0;

            g_stop = true;
        }

        DestroyWindow(hwnd);

        return 0;
    }

    case WM_DESTROY:
    {
        KillTimer(
            hwnd,
            10
        );

        DestroyBackBuffer();

        g_stop = true;

        if (g_worker.joinable())
        {
            g_worker.join();
        }

        PostQuitMessage(0);

        return 0;
    }

    default:
        break;
    }

    return DefWindowProcW(
        hwnd,
        message,
        wParam,
        lParam
    );
}

// ============================================================
// ENTRY POINT
// ============================================================

int APIENTRY wWinMain(
    HINSTANCE instance,
    HINSTANCE,
    LPWSTR,
    int showCommand)
{
    Gdiplus::GdiplusStartupInput startupInput{};

    const Gdiplus::Status startupStatus =
        Gdiplus::GdiplusStartup(
            &g_gdiplus,
            &startupInput,
            nullptr
        );

    if (startupStatus !=
        Gdiplus::Ok)
    {
        return 1;
    }

    WNDCLASSEXW windowClass{};

    windowClass.cbSize =
        sizeof(windowClass);

    windowClass.hInstance =
        instance;

    windowClass.lpfnWndProc =
        WndProc;

    windowClass.lpszClassName =
        L"ZarexLoaderRealGui";

    windowClass.hCursor =
        LoadCursorW(
            nullptr,
            IDC_ARROW
        );

    windowClass.hbrBackground =
        nullptr;

    windowClass.style =
        CS_HREDRAW |
        CS_VREDRAW;

    if (!RegisterClassExW(
        &windowClass))
    {
        Gdiplus::GdiplusShutdown(
            g_gdiplus
        );

        return 1;
    }

    g_hwnd =
        CreateWindowExW(
            WS_EX_APPWINDOW,
            windowClass.lpszClassName,
            APP_NAME,
            WS_POPUP,
            CW_USEDEFAULT,
            CW_USEDEFAULT,
            655,
            552,
            nullptr,
            nullptr,
            instance,
            nullptr
        );

    if (!g_hwnd)
    {
        Gdiplus::GdiplusShutdown(
            g_gdiplus
        );

        return 1;
    }

    // --------------------------------------------------------
    // DWM / DARK MODE
    // --------------------------------------------------------

    BOOL darkMode = TRUE;

    // DWMWA_USE_IMMERSIVE_DARK_MODE
    constexpr DWORD
        DWMWA_USE_IMMERSIVE_DARK_MODE_VALUE = 20;

    DwmSetWindowAttribute(
        g_hwnd,
        DWMWA_USE_IMMERSIVE_DARK_MODE_VALUE,
        &darkMode,
        sizeof(darkMode)
    );

    MARGINS margins{
        1,
        1,
        1,
        1
    };

    DwmExtendFrameIntoClientArea(
        g_hwnd,
        &margins
    );

    // --------------------------------------------------------
    // INITIAL SIZE
    // --------------------------------------------------------

    SetWindowClientSize(
        655,
        552
    );

    ShowWindow(
        g_hwnd,
        showCommand == SW_HIDE
        ? SW_SHOW
        : showCommand
    );

    UpdateWindow(
        g_hwnd
    );

    // --------------------------------------------------------
    // MESSAGE LOOP
    // --------------------------------------------------------

    MSG message{};

    while (GetMessageW(
        &message,
        nullptr,
        0,
        0) > 0)
    {
        TranslateMessage(
            &message
        );

        DispatchMessageW(
            &message
        );
    }

    if (g_worker.joinable())
    {
        g_stop = true;
        g_worker.join();
    }

    DestroyBackBuffer();

    Gdiplus::GdiplusShutdown(
        g_gdiplus
    );

    return static_cast<int>(
        message.wParam
        );
}
