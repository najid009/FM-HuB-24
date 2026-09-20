import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, useAuth } from './hooks/useAuth';
import { isSupabaseConfigured } from './lib/supabase';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    }
  }
});

function SetupRequired() {
  return (
    <div className="min-h-screen bg-zinc-950 text-white flex items-center justify-center px-4">
      <div className="w-full max-w-lg rounded-2xl border border-orange-500/30 bg-zinc-900 p-7 shadow-2xl">
        <p className="text-xs tracking-[0.3em] text-orange-400 uppercase">FMHuB24 control room</p>
        <h1 className="mt-3 text-2xl font-bold">Supabase configuration required</h1>
        <p className="mt-3 text-sm leading-6 text-zinc-400">
          The Admin Panel is running, but its Supabase connection is not configured yet.
          Create <code className="text-orange-300">admin-panel/.env</code> from the included
          <code className="text-orange-300"> .env.example</code>, add both values, then restart the dev server.
        </p>
        <pre className="mt-5 overflow-x-auto rounded-xl bg-zinc-950 p-4 text-xs text-zinc-300">VITE_SUPABASE_URL=https://your-project.supabase.co{`\n`}VITE_SUPABASE_ANON_KEY=your-anon-key</pre>
      </div>
    </div>
  );
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();

  if (loading) {
    return <div className="min-h-screen bg-black flex items-center justify-center"><div className="w-8 h-8 border-2 border-orange-500 border-t-transparent rounded-full animate-spin"></div></div>;
  }
  if (!user) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function PublicRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();

  if (loading) {
    return <div className="min-h-screen bg-black flex items-center justify-center"><div className="w-8 h-8 border-2 border-orange-500 border-t-transparent rounded-full animate-spin"></div></div>;
  }
  if (user) return <Navigate to="/" replace />;
  return <>{children}</>;
}

export default function App() {
  if (!isSupabaseConfigured) return <SetupRequired />;

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<PublicRoute><Login /></PublicRoute>} />
            <Route path="/" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}
