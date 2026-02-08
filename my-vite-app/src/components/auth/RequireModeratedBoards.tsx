import { useEffect, useState } from 'react';
import { Outlet } from 'react-router-dom';
import { listMyModeratedBoards } from '../../services/moderatorBoardsService';

export default function RequireModeratedBoards() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [hasBoards, setHasBoards] = useState(false);

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    setError(null);
    listMyModeratedBoards()
      .then((list) => {
        if (!mounted) return;
        setHasBoards((list ?? []).length > 0);
      })
      .catch((e) => {
        if (!mounted) return;
        setError(e instanceof Error ? e.message : String(e));
        setHasBoards(false);
      })
      .finally(() => {
        if (!mounted) return;
        setLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, []);

  if (loading) {
    return <div className="p-4 text-sm text-gray-600">加载版主权限中…</div>;
  }

  if (error) {
    return <div className="p-4 text-sm text-red-600">{error}</div>;
  }

  if (!hasBoards) {
    return (
      <div className="p-4">
        <div className="rounded border border-gray-200 bg-white p-4 text-sm text-gray-700">
          你目前没有被设置为任何版块的版主。
        </div>
      </div>
    );
  }

  return <Outlet />;
}

